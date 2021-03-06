/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.config.core.status;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

import org.eclipse.smarthome.config.core.status.events.ConfigStatusInfoEvent;
import org.eclipse.smarthome.core.common.ThreadPoolManager;
import org.eclipse.smarthome.core.events.EventPublisher;
import org.eclipse.smarthome.core.i18n.I18nProvider;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ConfigStatusService} provides the {@link ConfigStatusInfo} for a specific entity. For this purpose
 * it loops over all registered {@link ConfigStatusProvider}s and returns the {@link ConfigStatusInfo} for the matching
 * {@link ConfigStatusProvider}.
 *
 * @author Thomas Höfer - Initial contribution
 * @author Chris Jackson - Allow null messages
 */
public final class ConfigStatusService implements ConfigStatusCallback {

    private final Logger logger = LoggerFactory.getLogger(ConfigStatusService.class);

    private final List<ConfigStatusProvider> configStatusProviders = new CopyOnWriteArrayList<>();
    private EventPublisher eventPublisher;
    private I18nProvider i18nProvider;

    private final ExecutorService executorService = ThreadPoolManager
            .getPool(ConfigStatusService.class.getSimpleName());

    /**
     * Retrieves the {@link ConfigStatusInfo} of the entity by using the registered
     * {@link ConfigStatusProvider} that supports the given entity.
     *
     * @param entityId the id of the entity whose configuration status information is to be retrieved (must not
     *            be null or empty)
     * @param locale the locale to be used for the corresponding configuration status messages; if null then the
     *            default local will be used
     *
     * @return the {@link ConfigStatusInfo} or null if there is no {@link ConfigStatusProvider} registered that
     *         supports the given entity
     *
     * @throws IllegalArgumentException if given entityId is null or empty
     */
    public ConfigStatusInfo getConfigStatus(String entityId, Locale locale) {
        if (entityId == null || entityId.equals("")) {
            throw new IllegalArgumentException("EntityId must not be null or empty");
        }

        Locale loc = locale != null ? locale : Locale.getDefault();

        for (ConfigStatusProvider configStatusProvider : configStatusProviders) {
            if (configStatusProvider.supportsEntity(entityId)) {
                return getConfigStatus(configStatusProvider, entityId, loc);
            }
        }

        logger.debug("There is no config status provider for entity {} available.", entityId);

        return null;
    }

    @Override
    public void configUpdated(final ConfigStatusSource configStatusSource) {
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                final ConfigStatusInfo info = getConfigStatus(configStatusSource.entityId, null);

                if (info != null) {
                    if (eventPublisher != null) {
                        eventPublisher.post(new ConfigStatusInfoEvent(configStatusSource.getTopic(), info, null));
                    } else {
                        logger.warn("EventPublisher not available. Cannot post new config status for entity "
                                + configStatusSource.entityId);
                    }
                }
            }
        });
    }

    private ConfigStatusInfo getConfigStatus(ConfigStatusProvider configStatusProvider, String entityId,
            Locale locale) {
        Collection<ConfigStatusMessage> configStatus = configStatusProvider.getConfigStatus();
        if (configStatus == null) {
            logger.debug("Cannot provide config status for entity {} because its config status provider returned null.",
                    entityId);
            return null;
        }

        Bundle bundle = FrameworkUtil.getBundle(configStatusProvider.getClass());

        ConfigStatusInfo info = new ConfigStatusInfo();

        for (ConfigStatusMessage configStatusMessage : configStatus) {
            String message = null;
            if (configStatusMessage.messageKey != null) {
                message = i18nProvider.getText(bundle, configStatusMessage.messageKey, null, locale,
                        configStatusMessage.arguments);
                if (message == null) {
                    logger.warn(
                            "No translation found for key {} and config status provider {}. Will ignore the config status message.",
                            configStatusMessage.messageKey, configStatusProvider.getClass().getSimpleName());
                    continue;
                }
            }
            info.add(new ConfigStatusMessage(configStatusMessage.parameterName, configStatusMessage.type, message,
                    configStatusMessage.statusCode));

        }

        return info;
    }

    protected void addConfigStatusProvider(ConfigStatusProvider configStatusProvider) {
        configStatusProvider.setConfigStatusCallback(this);
        configStatusProviders.add(configStatusProvider);
    }

    protected void removeConfigStatusProvider(ConfigStatusProvider configStatusProvider) {
        configStatusProvider.setConfigStatusCallback(null);
        configStatusProviders.remove(configStatusProvider);
    }

    protected void setEventPublisher(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    protected void unsetEventPublisher(EventPublisher eventPublisher) {
        this.eventPublisher = null;
    }

    protected void setI18nProvider(I18nProvider i18nProvider) {
        this.i18nProvider = i18nProvider;
    }

    protected void unsetI18nProvider(I18nProvider i18nProvider) {
        this.i18nProvider = null;
    }
}
