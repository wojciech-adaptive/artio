/*
 * Copyright 2015-2019 Adaptive Financial Consulting Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.artio.engine.logger;

import io.aeron.Aeron;
import io.aeron.ExclusivePublication;
import io.aeron.driver.MediaDriver;
import org.agrona.DirectBuffer;
import org.agrona.collections.LongArrayList;
import org.agrona.concurrent.EpochNanoClock;
import org.agrona.concurrent.OffsetEpochNanoClock;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.After;
import org.junit.Test;
import uk.co.real_logic.artio.CommonConfiguration;
import uk.co.real_logic.artio.TestFixtures;
import uk.co.real_logic.artio.Timing;
import uk.co.real_logic.artio.dictionary.generation.Exceptions;
import uk.co.real_logic.artio.ilink.ILinkMessageConsumer;
import uk.co.real_logic.artio.messages.MessageHeaderEncoder;
import uk.co.real_logic.artio.messages.ReplayerTimestampDecoder;
import uk.co.real_logic.artio.messages.ReplayerTimestampEncoder;
import uk.co.real_logic.artio.protocol.GatewayPublication;

import static io.aeron.CommonContext.IPC_CHANNEL;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static uk.co.real_logic.artio.CommonConfiguration.DEFAULT_INBOUND_LIBRARY_STREAM;
import static uk.co.real_logic.artio.CommonConfiguration.DEFAULT_OUTBOUND_LIBRARY_STREAM;
import static uk.co.real_logic.artio.engine.EngineConfiguration.DEFAULT_OUTBOUND_REPLAY_STREAM;
import static uk.co.real_logic.artio.messages.MessageHeaderDecoder.ENCODED_LENGTH;

public abstract class AbstractFixMessageLoggerTest
{
    static final byte[] FAKE_FIX_MESSAGE = "ABCDEFGHI".getBytes(US_ASCII);
    static final DirectBuffer FAKE_MESSAGE_BUFFER = new UnsafeBuffer(FAKE_FIX_MESSAGE);
    static final int LIBRARY_ID = 1;
    static final long SESSION_ID = 2;
    static final int SEQUENCE_INDEX = 3;
    static final long CONNECTION_ID = 4;

    int compactionSize;

    final EpochNanoClock clock = new OffsetEpochNanoClock();
    final LongArrayList timestamps = new LongArrayList();

    private final FixMessageConsumer fixConsumer = (message, buffer, offset, length, header) ->
    {
        timestamps.add(message.timestamp());
    };

    private MediaDriver mediaDriver;
    private Aeron aeron;
    private FixMessageLogger logger;

    void setup(final ILinkMessageConsumer iLinkMessageConsumer)
    {
        mediaDriver = TestFixtures.launchJustMediaDriver();
        aeron = Aeron.connect();

        final FixMessageLogger.Configuration config = new FixMessageLogger.Configuration()
            .fixMessageConsumer(fixConsumer)
            .iLinkMessageConsumer(iLinkMessageConsumer)
            .compactionSize(compactionSize);
        logger = new FixMessageLogger(config);
    }

    @After
    public void teardown()
    {
        Exceptions.closeAll(aeron, mediaDriver);
        Exceptions.closeAll(logger);
    }

    @Test
    public void shouldReOrderMessagesByTimestamp()
    {
        final GatewayPublication inboundPublication = newPublication(DEFAULT_INBOUND_LIBRARY_STREAM);
        final GatewayPublication outboundPublication = newPublication(DEFAULT_OUTBOUND_LIBRARY_STREAM);
        final ExclusivePublication replayStream = aeron.addExclusivePublication(
            IPC_CHANNEL,
            DEFAULT_OUTBOUND_REPLAY_STREAM);

        onMessage(inboundPublication, 2);
        onMessage(inboundPublication, 3);
        onMessage(inboundPublication, 4);
        onMessage(outboundPublication, 1);
        onMessage(outboundPublication, 5);
        onMessage(outboundPublication, 7);
        onMessage(inboundPublication, 6);
        onReplayerTimestamp(replayStream, 10);

        assertEventuallyReceives(6);
        assertThat(timestamps, contains(1L, 2L, 3L, 4L, 5L, 6L));
        timestamps.clear();

        // A message arriving later
        onMessage(inboundPublication, 8);
        assertEventuallyReceives(1);
        assertThat(timestamps, contains(7L));
        timestamps.clear();

        assertThat("failed to reshuffle", logger.bufferPosition(), lessThanOrEqualTo(compactionSize));

        onMessage(inboundPublication, 9);
        onMessage(outboundPublication, 10);
        assertEventuallyReceives(2);
        assertThat(timestamps, contains(8L, 9L));
    }

    private void assertEventuallyReceives(final int messageCount)
    {
        Timing.assertEventuallyTrue(
            () -> "Failed to receive a message: " + timestamps,
            () ->
            {
                logger.doWork();
                return timestamps.size() == messageCount;
            },
            1000,
            () ->
            {
            });
    }

    abstract void onMessage(GatewayPublication inboundPublication, int timestamp);

    private void onReplayerTimestamp(final ExclusivePublication replayStream, final long timestampInNs)
    {
        final UnsafeBuffer timestampBuffer = new UnsafeBuffer(new byte[
            ENCODED_LENGTH + ReplayerTimestampDecoder.BLOCK_LENGTH]);
        final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
        final ReplayerTimestampEncoder replayerTimestampEncoder = new ReplayerTimestampEncoder();
        replayerTimestampEncoder
            .wrapAndApplyHeader(timestampBuffer, 0, headerEncoder)
            .timestamp(timestampInNs);

        final long position = replayStream.offer(timestampBuffer);
        assertThat(position, greaterThan(0L));
    }

    private GatewayPublication newPublication(final int streamId)
    {
        final ExclusivePublication publication = aeron.addExclusivePublication(
            IPC_CHANNEL, streamId);
        return new GatewayPublication(
            publication,
            null,
            CommonConfiguration.backoffIdleStrategy(),
            clock,
            1);
    }
}
