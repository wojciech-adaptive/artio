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
package uk.co.real_logic.fix_gateway.stress;

public class StressConfiguration
{
    public static final int PORT = Integer.getInteger("fix.stress.port", 9999);
    public static final String ACCEPTOR_ID = "ACC";
    public static final String INITIATOR_ID = "INIT";
    public static final int NUM_SESSIONS = Integer.getInteger("fix.stress.sessions", 10);
    public static final int MESSAGES_EXCHANGED = Integer.getInteger("fix.stress.messages", 3);
    public static final boolean PRINT_EXCHANGE = Boolean.getBoolean("fix.stress.printExchange");
    public static final long SEED = Long.getLong("fix.stress.seed", 42424242L);
    public static final int MIN_LENGTH = Integer.getInteger("fix.stress.messages.minLength", 1);
    public static final int MAX_LENGTH = Integer.getInteger("fix.stress.messages.maxLength", 20);
}
