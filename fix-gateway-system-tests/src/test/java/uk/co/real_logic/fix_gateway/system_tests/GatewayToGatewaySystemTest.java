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
package uk.co.real_logic.fix_gateway.system_tests;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import uk.co.real_logic.fix_gateway.engine.FixEngine;
import uk.co.real_logic.fix_gateway.engine.SessionInfo;
import uk.co.real_logic.fix_gateway.engine.framer.LibraryInfo;
import uk.co.real_logic.fix_gateway.library.FixLibrary;
import uk.co.real_logic.fix_gateway.messages.SessionReplyStatus;
import uk.co.real_logic.fix_gateway.messages.SessionState;
import uk.co.real_logic.fix_gateway.session.Session;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static uk.co.real_logic.fix_gateway.CommonMatchers.hasConnectionId;
import static uk.co.real_logic.fix_gateway.TestFixtures.launchMediaDriver;
import static uk.co.real_logic.fix_gateway.Timing.assertEventuallyTrue;
import static uk.co.real_logic.fix_gateway.system_tests.SystemTestUtil.*;

public class GatewayToGatewaySystemTest extends AbstractGatewayToGatewaySystemTest
{

    @Before
    public void launch()
    {
        mediaDriver = launchMediaDriver();

        acceptingEngine = launchAcceptingEngine(port, ACCEPTOR_ID, INITIATOR_ID);
        initiatingEngine = launchInitiatingGateway(initAeronPort);

        acceptingLibrary = newAcceptingLibrary(acceptingSessionHandler);
        initiatingLibrary = newInitiatingLibrary(initAeronPort, initiatingSessionHandler, 1);

        connectSessions();
    }

    @Test
    public void sessionHasBeenInitiated() throws InterruptedException
    {
        assertNotNull("Accepting Session not been setup", acceptingSession);
    }

    @Test
    public void messagesCanBeSentFromInitiatorToAcceptor()
    {
        sendTestRequest(initiatingSession);

        assertReceivedTestRequest(initiatingLibrary, acceptingLibrary, acceptingOtfAcceptor);
    }

    @Test
    public void messagesCanBeSentFromAcceptorToInitiator()
    {
        sendTestRequest(acceptingSession);

        assertReceivedTestRequest(initiatingLibrary, acceptingLibrary, initiatingOtfAcceptor);
    }

    @Test
    public void initiatorSessionCanBeDisconnected()
    {
        initiatingSession.startLogout();

        assertSessionsDisconnected();
    }

    @Test
    public void acceptorSessionCanBeDisconnected()
    {
        acceptingSession.startLogout();

        assertSessionsDisconnected();
    }

    @Test
    public void gatewayProcessesResendRequests()
    {
        messagesCanBeSentFromInitiatorToAcceptor();

        sendResendRequest();

        assertMessageResent();
    }

    @Test
    public void twoSessionsCanConnect()
    {
        acceptingSession.startLogout();
        assertSessionsDisconnected();

        acceptingOtfAcceptor.messages().clear();
        initiatingOtfAcceptor.messages().clear();

        connectSessions();

        sendTestRequest(initiatingSession);
        assertReceivedTestRequest(initiatingLibrary, acceptingLibrary, acceptingOtfAcceptor);
    }

    @Test
    public void sessionsListedInAdminApi()
    {
        final List<LibraryInfo> libraries = initiatingEngine.libraries(ADMIN_IDLE_STRATEGY);
        assertThat(libraries, hasSize(1));

        final LibraryInfo library = libraries.get(0);
        assertEquals(initiatingLibrary.libraryId(), library.libraryId());

        final List<SessionInfo> sessions = library.sessions();
        assertThat(sessions, hasSize(1));

        final SessionInfo sessionInfo = sessions.get(0);
        assertThat(sessionInfo.address(), containsString("localhost"));
        assertThat(sessionInfo.address(), containsString(String.valueOf(port)));
        assertEquals(initiatingSession.connectionId(), sessionInfo.connectionId());

        assertEquals(initiatingSession.connectedPort(), port);
        assertEquals(initiatingSession.connectedHost(), "localhost");

        assertNotEquals(0, acceptingSession.connectedPort());
        assertEquals("127.0.0.1", acceptingSession.connectedHost());
    }

    @Test
    public void multipleLibrariesCanExchangeMessages()
    {
        final int initiator1MessageCount = initiatingOtfAcceptor.messages().size();

        final FakeOtfAcceptor initiatingOtfAcceptor2 = new FakeOtfAcceptor();
        final FakeSessionHandler initiatingSessionHandler2 = new FakeSessionHandler(initiatingOtfAcceptor2);
        try (final FixLibrary library2 = newInitiatingLibrary(initAeronPort, initiatingSessionHandler2, 2))
        {
            final Session session2 = initiate(library2, port, INITIATOR_ID2, ACCEPTOR_ID);

            assertConnected(session2);
            sessionLogsOn(library2, acceptingLibrary, session2);
            final Session acceptingSession2 = acceptSession(acceptingSessionHandler, acceptingLibrary);

            sendTestRequest(acceptingSession2);
            assertReceivedTestRequest(library2, acceptingLibrary, initiatingOtfAcceptor2);

            assertOriginalLibraryDoesNotReceiveMessages(initiator1MessageCount);
        }
    }

    @Test
    public void sequenceNumbersShouldResetOverDisconnects()
    {
        sendTestRequest(initiatingSession);
        assertReceivedTestRequest(initiatingLibrary, acceptingLibrary, acceptingOtfAcceptor);
        assertSequenceFromInitToAcceptAt(2);

        initiatingSession.startLogout();

        assertSessionsDisconnected();

        connectSessions();

        sendTestRequest(initiatingSession);
        assertReceivedTestRequest(initiatingLibrary, acceptingLibrary, acceptingOtfAcceptor, 4);
        assertSequenceFromInitToAcceptAt(2);
    }

    @Test
    public void acceptorsShouldHandleInitiatorDisconnectsGracefully()
    {
        //initiatingLibrary.close();
        initiatingEngine.close();

        //LockSupport.parkNanos(10_000_000_000L);
    }

    @Test
    public void librariesShouldBeAbleToReleaseInitiatedSessionToTheGateway()
    {
        releaseSessionToGateway(initiatingSession, initiatingLibrary, initiatingEngine);
    }

    @Test
    public void librariesShouldBeAbleToReleaseAcceptedSessionToTheGateway()
    {
        releaseSessionToGateway(acceptingSession, acceptingLibrary, acceptingEngine);
    }

    private void releaseSessionToGateway(
        final Session session,
        final FixLibrary library,
        final FixEngine engine)
    {
        final long connectionId = session.connectionId();

        final SessionReplyStatus status = library.releaseToGateway(session, ADMIN_IDLE_STRATEGY);

        assertEquals(SessionReplyStatus.OK, status);
        assertEquals(SessionState.DISABLED, session.state());
        assertThat(library.sessions(), hasSize(0));

        final List<SessionInfo> sessions = engine.gatewaySessions(ADMIN_IDLE_STRATEGY);
        assertThat(sessions,
            contains(hasConnectionId(connectionId)));
    }

    @Test
    public void librariesShouldBeAbleToAcquireReleasedInitiatedSessions()
    {
        reacquireReleasedSession(initiatingSession, initiatingLibrary, initiatingEngine);
    }

    @Test
    public void librariesShouldBeAbleToAcquireReleasedAcceptedSessions()
    {
        reacquireReleasedSession(acceptingSession, acceptingLibrary, acceptingEngine);
    }

    private void reacquireReleasedSession(
        final Session session, final FixLibrary library, final FixEngine engine)
    {
        final long connectionId = session.connectionId();

        library.releaseToGateway(session, ADMIN_IDLE_STRATEGY);

        final SessionReplyStatus status = library.acquireSession(connectionId, ADMIN_IDLE_STRATEGY);

        assertEquals(SessionReplyStatus.OK, status);

        assertThat(engine.gatewaySessions(ADMIN_IDLE_STRATEGY), hasSize(0));

        final List<LibraryInfo> libraries = engine.libraries(ADMIN_IDLE_STRATEGY);
        assertThat(libraries.get(0).sessions(),
            contains(hasConnectionId(connectionId)));

        final List<Session> sessions = library.sessions();
        assertThat(sessions, hasSize(1));

        final Session newSession = sessions.get(0);
        assertTrue(newSession.isConnected());
        assertEquals(connectionId, newSession.connectionId());
        assertEquals(session.id(), newSession.id());
        assertEquals(session.username(), newSession.username());
        assertEquals(session.password(), newSession.password());
    }

    @Test
    public void enginesShouldManageAcceptingSession()
    {
        engineShouldManageSession(
            acceptingSession, acceptingLibrary,
            initiatingSession, initiatingLibrary, initiatingOtfAcceptor);
    }

    @Ignore
    @Test
    public void enginesShouldManageInitiatingSession()
    {
        engineShouldManageSession(
            initiatingSession, initiatingLibrary,
            acceptingSession, acceptingLibrary, acceptingOtfAcceptor);
    }

    private void engineShouldManageSession(
        final Session session, final FixLibrary library,
        final Session otherSession, final FixLibrary otherLibrary, final FakeOtfAcceptor otherAcceptor)
    {
        final long connectionId = session.connectionId();

        library.releaseToGateway(session, ADMIN_IDLE_STRATEGY);

        sendTestRequest(otherSession);

        assertReceivedHeartbeat(otherLibrary, otherAcceptor);

        final SessionReplyStatus status = library.acquireSession(connectionId, ADMIN_IDLE_STRATEGY);
        assertEquals(SessionReplyStatus.OK, status);

        sendTestRequest(otherSession);

        assertReceivedHeartbeat(otherLibrary, otherAcceptor);
    }

    private void assertReceivedHeartbeat(final FixLibrary library, final FakeOtfAcceptor acceptor)
    {
        assertEventuallyTrue("Failed to received heartbeat", () ->
        {
            library.poll(1);
            return acceptor.messages()
                           .stream()
                           .anyMatch(fixMessage -> fixMessage.get(35).equals("0"));
        });
    }

    @Test
    public void librariesShouldNotBeAbleToAcquireSessionsThatDontExist()
    {
        final SessionReplyStatus status = initiatingLibrary.acquireSession(42, ADMIN_IDLE_STRATEGY);

        assertEquals(SessionReplyStatus.UNKNOWN_SESSION, status);
    }

}
