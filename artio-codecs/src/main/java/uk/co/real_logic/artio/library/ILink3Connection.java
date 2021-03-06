/*
 * Copyright 2020 Monotonic Ltd.
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
package uk.co.real_logic.artio.library;

import org.agrona.sbe.MessageEncoderFlyweight;
import uk.co.real_logic.artio.messages.DisconnectReason;

/**
 * Represents a Session Connection of the iLink3 protocol.
 * This is a FIXP session protocol with SBE encoded binary messages. Unlike FIX it possible to have multiple connections
 * open with the same session id.
 *
 * NB: This is an experimental API and is subject to change or potentially removal.
 */
public abstract class ILink3Connection
{
    public static final long NOT_AWAITING_RETRANSMIT = -1L;

    /**
     * Defines the internal state of the Session, this can be accessed using
     * the {@link ILink3Connection#state()} method.
     */
    public enum State
    {
        /** The TCP connection has been established, but the negotiate not sent.*/
        CONNECTED,
        /** The Negotiate message sent but no reply received yet. */
        SENT_NEGOTIATE,
        /** The Negotiate message hasn't been sent due to back-pressure in Artio, retrying attempt to send. */
        RETRY_NEGOTIATE,

        /** Received a Negotiate Reject message. */
        NEGOTIATE_REJECTED,
        /** Negotiate accepted, Establish not sent */
        NEGOTIATED,
        /** Negotiate accepted, Establish sent */
        SENT_ESTABLISH,
        /**
         * Negotiate accepted, The Establish message hasn't been sent due to back-pressure in Artio,
         * retrying attempt to send.
         * */
        RETRY_ESTABLISH,
        /** Received an Establish Reject message. */
        ESTABLISH_REJECTED,
        /** Establish accepted, messages can be exchanged */
        ESTABLISHED,
        /** The session is currently retransmitting messages in response to a NotApplied message. */
        RETRANSMITTING,
        /**
         * keepAliveInterval has expired without receiving a message from the exchange - we are waiting that long again
         * before terminating.
         */
        AWAITING_KEEPALIVE,
        /**
         * An initiating Terminate message hasn't been sent due to back-pressure in Artio, retrying attempt to send.
         */
        RESEND_TERMINATE,
        /**
         * An acknowledging Terminate message hasn't been sent due to back-pressure in Artio, retrying attempt to send.
         */
        RESEND_TERMINATE_ACK,
        /**
         * We are awaiting a reply to a Terminate message. If keepAliveTimeout expires without a reply the TCP
         * connection will be disconnected.
         */
        UNBINDING,
        /**
         * This session has sent a Terminate message in order to initiate a terminate, but has not received a reply
         * yet.
         */
        SENT_TERMINATE,
        /** The session has been disconnected at the TCP level. */
        UNBOUND
    }

    // -----------------------------------------------
    // Operations
    // -----------------------------------------------

    /**
     * Tries to send a business layer message with no variable length or group fields. This method claims a buffer
     * slot for the flyweight object to wrap. If this method returns successfully then the flyweight's fields can be
     * used. After the flyweight is filled in then the {@link #commit()} method should be used to commit the message.
     *
     * @param message the business layer message to send.
     * @return the position in the stream that corresponds to the end of this message or a negative
     * number indicating an error status.
     * @see #tryClaim(MessageEncoderFlyweight, int)
     */
    public abstract long tryClaim(MessageEncoderFlyweight message);

    /**
     * Tries to send a business layer message with a variable length or group fields. This method claims a buffer
     * slot for the flyweight object to wrap. If this method returns successfully then the flyweight's fields can be
     * used. After the flyweight is filled in then the {@link #commit()} method should be used to commit the message.
     *
     * @param message the business layer message to send.
     * @param variableLength the total size of all the variable length and group fields in the message including their
     *                       headers. Aka the total length of the message minus it's block length.
     * @return the position in the stream that corresponds to the end of this message or a negative
     * number indicating an error status.
     * @see #tryClaim(MessageEncoderFlyweight)
     */
    public abstract long tryClaim(MessageEncoderFlyweight message, int variableLength);

    /**
     * Commit a message that has been claimed. Do not overlap sending other messages or polling the FixLibrary
     * with claiming and committing your own message - just claim and commit it immediately. If an error happens during
     * the initialization of the message then you should call {@link #abort()}
     *
     * @see #tryClaim(MessageEncoderFlyweight)
     * @see #tryClaim(MessageEncoderFlyweight, int)
     * @see #abort()
     */
    public abstract void commit();

    /**
     * Abort a message that has been claimed. If an error happens when initialising a message flyweight after a
     * call to <code>tryClaim()</code> then this method can be called in order to abort the message and not send it.
     *
     * @see #tryClaim(MessageEncoderFlyweight)
     * @see #tryClaim(MessageEncoderFlyweight, int)
     * @see #abort()
     */
    public abstract void abort();

    /**
     * Try to send a sequence message indicating the current sent sequence number position. This can be combined
     * with {@link #nextSentSeqNo(long)} in order to move the sent sequence number forward in agreement with the
     * exchange.
     *
     * @return the position in the stream that corresponds to the end of this message or a negative
     * number indicating an error status.
     */
    public abstract long trySendSequence();

    /**
     * Disconnect your session, providing a reason. This is an immediate TCP disconnect with no Terminate message
     * sent.
     *
     * @param reason the reason you disconnected.
     * @return the position in the stream that corresponds to the end of this message or a negative
     * number indicating an error status.
     */
    public abstract long requestDisconnect(DisconnectReason reason);

    /**
     * Initiate a termination. This sends a Terminate message to initiate the termination. Artio's session will await
     * an acknowledging Terminate message from the exchange. If keepAliveInterval elapses without a reply then a TCP
     * disconnect will happen.
     *
     * @param shutdown the shutdown text to send in the Terminate message
     * @param errorCodes the error codes to send in the Terminate message
     * @return the position in the stream that corresponds to the end of this message or a negative
     * number indicating an error status.
     */
    public abstract long terminate(String shutdown, int errorCodes);

    /**
     * Send a custom retransmit request.
     *
     * @param uuid the UUID of the connection to request a retransmit request. This doesn't necessarily have to be the
     *             current UUID, but it does have to be one for the same session on the same market segment.
     * @param fromSeqNo the sequence number to start from.
     * @param msgCount the number of messages to request a retransmit of.
     * @return the position in the stream that corresponds to the end of this message or a negative
     * number indicating an error status.
     */
    public abstract long tryRetransmitRequest(long uuid, long fromSeqNo, int msgCount);

    // -----------------------------------------------
    // Accessors
    // -----------------------------------------------

    /**
     * Gets the UUID of the current connection for this session.
     *
     * @return the UUID of the current connection for this session.
     */
    public abstract long uuid();

    /**
     * Gets the UUID of the last success connection for this session.
     *
     * @return the UUID of the last success connection for this session.
     */
    public abstract long lastUuid();

    /**
     * Gets the Artio connectionId of the current connection for this session.
     *
     * @return the Artio connectionId of the current connection for this session.
     */
    public abstract long connectionId();

    /**
     * Gets the current State of this session.
     *
     * @return the current State of this session.
     */
    public abstract State state();

    /**
     * Gets the next sequence number to be used when sending a new business layer message.
     *
     * @return the next sequence number to be used when sending  a new business layer message.
     */
    public abstract long nextSentSeqNo();

    /**
     * Sets the next sequence number to be used when sending a new business layer message.
     *
     * @param nextSentSeqNo the next sequence number to be used when sending  a new business layer message.
     */
    public abstract void nextSentSeqNo(long nextSentSeqNo);

    /**
     * Gets the next sequence number to be expected when receiving a new business layer message.
     *
     * @return the next sequence number to be expected when receiving  a new business layer message.
     */
    public abstract long nextRecvSeqNo();

    /**
     * Sets the next sequence number to be expected when receiving a new business layer message.
     *
     * @param nextRecvSeqNo the next sequence number to be expected when receiving  a new business layer message.
     */
    public abstract void nextRecvSeqNo(long nextRecvSeqNo);

    /**
     * Gets the next received sequence number that will fill the current retransmit request. If there is no
     * retransmit operation in process NOT_AWAITING_RETRANSMIT will be returned.
     *
     * @return the next received sequence number that will fill the current retransmit request.
     */
    public abstract long retransmitFillSeqNo();

    /**
     * Gets the next sequence number that Artio expects to received in the current retransmit request. If there is no
     * retransmit operation in process NOT_AWAITING_RETRANSMIT will be returned.
     *
     * @return the next sequence number that Artio expects to received in the current retransmit request.
     */
    public abstract long nextRetransmitSeqNo();

    /**
     * Check if a message can be sent. This is when you're in the ESTABLISHED or AWAITING_KEEPALIVE state.
     *
     * @return true if a message can be sent, false otherwise
     */
    public abstract boolean canSendMessage();

    // -----------------------------------------------
    // Internal Methods below, not part of the public API
    // -----------------------------------------------

    abstract int poll(long timeInMs);

    abstract void onReplayComplete();

    abstract void fullyUnbind();

    abstract void unbindState();

}
