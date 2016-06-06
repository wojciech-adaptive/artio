/*
 * Copyright 2015-2016 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.fix_gateway.replication;

import io.aeron.Publication;
import io.aeron.logbuffer.BufferClaim;
import io.aeron.logbuffer.ControlledFragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;

import static io.aeron.logbuffer.ControlledFragmentHandler.Action.ABORT;
import static io.aeron.logbuffer.ControlledFragmentHandler.Action.CONTINUE;

public class OutboundPipe implements ControlledFragmentHandler
{
    private final BufferClaim bufferClaim = new BufferClaim();

    private final Publication publication;
    private final ClusterNode node;
    private final ClusterableSubscription subscription;

    public OutboundPipe(final Publication publication, final ClusterNode node)
    {
        this.publication = publication;
        this.node = node;
        this.subscription = publication != null ? node.subscription(publication.streamId()) : null;
    }

    public int poll(final int fragmentLimit)
    {
        if (publication != null && node.isLeader())
        {
            final int sent = subscription.controlledPoll(this, fragmentLimit);
            return sent;
        }

        return 0;
    }

    public Action onFragment(final DirectBuffer buffer, final int offset, final int length, final Header header)
    {
        final long position = publication.tryClaim(length, bufferClaim);
        if (position < 0)
        {
            return ABORT;
        }

        bufferClaim.buffer().putBytes(bufferClaim.offset(), buffer, offset, length);

        bufferClaim.commit();
        return CONTINUE;
    }
}