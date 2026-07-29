package com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.util.internal;

import reactor.blockhound.BlockHound;
import reactor.blockhound.integration.BlockHoundIntegration;

// nacos-client-3.0.3.jar registers this exact class name in
// META-INF/services/reactor.blockhound.integration.BlockHoundIntegration but never
// actually packages it (a shading/minimization defect upstream). BlockHound.install()
// resolves that ServiceLoader entry eagerly and fails the whole test run with
// ServiceConfigurationError if the class can't be found anywhere on the classpath.
// Providing a no-op stub under the same FQN satisfies ServiceLoader resolution.
public final class Hidden {

    private Hidden() {}

    public static final class NettyBlockHoundIntegration implements BlockHoundIntegration {
        @Override
        public void applyTo(BlockHound.Builder builder) {
        }
    }
}
