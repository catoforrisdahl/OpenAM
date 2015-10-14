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

import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.Requests;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.http.HttpContext;
import org.forgerock.openam.selfservice.config.ConsoleConfig;
import org.forgerock.openam.selfservice.config.ConsoleConfigExtractor;
import org.forgerock.openam.selfservice.config.ConsoleConfigHandler;
import org.forgerock.openam.selfservice.config.ServiceProvider;
import org.forgerock.openam.selfservice.config.ServiceProviderFactory;
import org.forgerock.selfservice.core.config.ProcessInstanceConfig;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;

/**
 * Unit test for {@link SelfServiceRequestHandler}.
 *
 * @since 13.0.0
 */
public final class SelfServiceRequestHandlerTest {

    @Mock
    private ConsoleConfigHandler consoleConfigHandler;
    @Mock
    private ConsoleConfigExtractor<ConsoleConfig> configExtractor;
    @Mock
    private ServiceProviderFactory providerFactory;
    @Mock
    private ServiceProvider<ConsoleConfig> configProvider;
    @Mock
    private ConsoleConfig consoleConfig;
    @Mock
    private RequestHandler underlyingService;

    private HttpContext context;

    private RequestHandler selfServiceHandler;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        context = new HttpContext(json(object(field("headers", Collections.emptyMap()),
                field("parameters", Collections.emptyMap()))), null);

        selfServiceHandler = new SelfServiceRequestHandler<>(consoleConfigHandler, configExtractor, providerFactory);
    }

    @Test
    public void initialReadCallsIntoUnderlyingService() throws ResourceException {
        // When
        ReadRequest request = Requests.newReadRequest("/someEndpoint");

        given(consoleConfigHandler.getConfig("/", configExtractor)).willReturn(consoleConfig);
        given(providerFactory.getProvider(consoleConfig)).willReturn(configProvider);
        given(configProvider.isServiceEnabled(consoleConfig)).willReturn(true);

        given(configProvider.getService(consoleConfig, context, "/")).willReturn(underlyingService);

        // Given
        selfServiceHandler.handleRead(context, request);

        // Then
        verify(underlyingService).handleRead(context, request);
    }

    @Test
    public void initialActionCallsIntoUnderlyingService() throws ResourceException {
        // When
        ActionRequest request = Requests.newActionRequest("/someEndpoint", "submitRequirements");

        given(consoleConfigHandler.getConfig("/", configExtractor)).willReturn(consoleConfig);
        given(providerFactory.getProvider(consoleConfig)).willReturn(configProvider);
        given(configProvider.isServiceEnabled(consoleConfig)).willReturn(true);
        ProcessInstanceConfig config = new ProcessInstanceConfig();
        given(configProvider.getService(consoleConfig, context, "/")).willReturn(underlyingService);

        // Given
        selfServiceHandler.handleAction(context, request);

        // Then
        verify(underlyingService).handleAction(context, request);
    }

}