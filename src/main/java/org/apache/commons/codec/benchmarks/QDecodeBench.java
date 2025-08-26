/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.codec.benchmarks;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.net.QCodec;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

public class QDecodeBench {


    @State(Scope.Thread)
    public static class MyState {


        @Setup(Level.Invocation)
        public void doSetup() {
            qCodec = new QCodec();
            coded = "=?UTF-8?Q?=3D Hello there =3D=0D=0A?=";
        }


        public String coded;
        public QCodec qCodec;
    }

    @Benchmark
    public void testEncodeMethod(MyState myState, Blackhole blackhole) throws DecoderException {
        blackhole.consume(myState.qCodec.decode(myState.coded));
    }

}
