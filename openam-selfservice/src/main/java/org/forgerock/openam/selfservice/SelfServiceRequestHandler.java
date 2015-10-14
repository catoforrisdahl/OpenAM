/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2015 ForgeRock AS.
 */
package org.forgerock.openam.selfservice;

import org.forgerock.json.resource.AbstractRequestHandler;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.ActionResponse;
import org.forgerock.json.resource.InternalServerErrorException;
import org.forgerock.json.resource.NotSupportedException;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.openam.rest.RealmContext;
import org.forgerock.openam.selfservice.config.ConsoleConfig;
import org.forgerock.openam.selfservice.config.ConsoleConfigChangeListener;
import org.forgerock.openam.selfservice.config.ConsoleConfigExtractor;
import org.forgerock.openam.selfservice.config.ConsoleConfigHandler;
import org.forgerock.openam.selfservice.config.ServiceProvider;
import org.forgerock.openam.selfservice.config.ServiceProviderFactory;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract request handler used to setup the self services.
 *
 * @since 13.0.0
 */
final class SelfServiceRequestHandler<C extends ConsoleConfig>
        extends AbstractRequestHandler implements ConsoleConfigChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(SelfServiceRequestHandler.class);

    private final Map<String, RequestHandler> serviceCache;
    private final ConsoleConfigHandler consoleConfigHandler;
    private final ConsoleConfigExtractor<C> configExtractor;
    private final ServiceProviderFactory providerFactory;

    /**
     * Constructs a new self service.
     *
     * @param consoleConfigHandler
     *         console configuration handler
     * @param configExtractor
     *         configuration extractor
     * @param providerFactory
     *         service provider factory
     */
    @Inject
    public SelfServiceRequestHandler(ConsoleConfigHandler consoleConfigHandler,
            ConsoleConfigExtractor<C> configExtractor, ServiceProviderFactory providerFactory) {

        serviceCache = new ConcurrentHashMap<>();
        this.consoleConfigHandler = consoleConfigHandler;
        this.configExtractor = configExtractor;
        this.providerFactory = providerFactory;

        consoleConfigHandler.registerListener(this);
    }

    @Override
    public Promise<ResourceResponse, ResourceException> handleRead(Context context, ReadRequest request) {
        try {
            return getService(context).handleRead(context, request);
        } catch (NotSupportedException nsE) {
            return nsE.asPromise();
        } catch (RuntimeException rE) {
            logger.error("Unable to handle read", rE);
            return new InternalServerErrorException("Unable to handle read", rE).asPromise();
        }
    }

    @Override
    public Promise<ActionResponse, ResourceException> handleAction(Context context, ActionRequest request) {
        try {
            return getService(context).handleAction(context, request);
        } catch (NotSupportedException nsE) {
            return nsE.asPromise();
        } catch (RuntimeException rE) {
            logger.error("Unable to handle action", rE);
            return new InternalServerErrorException("Unable to handle action", rE).asPromise();
        }
    }

    private RequestHandler getService(Context context) throws NotSupportedException {
        String realm = RealmContext.getRealm(context);
        RequestHandler service = serviceCache.get(realm);

        if (service == null) {
            synchronized (serviceCache) {
                service = serviceCache.get(realm);

                if (service == null) {
                    service = createNewService(context, realm);
                    serviceCache.put(realm, service);
                }
            }
        }

        return service;
    }

    private RequestHandler createNewService(Context context, String realm) throws NotSupportedException {
        C consoleConfig = consoleConfigHandler.getConfig(realm, configExtractor);
        ServiceProvider<C> serviceProvider = providerFactory.getProvider(consoleConfig);

        if (!serviceProvider.isServiceEnabled(consoleConfig)) {
            throw new NotSupportedException("Service not configured");
        }

        return serviceProvider.getService(consoleConfig, context, realm);
    }

    @Override
    public final void configUpdate(String realm) {
        synchronized (serviceCache) {
            serviceCache.remove(realm);
        }
    }

}
