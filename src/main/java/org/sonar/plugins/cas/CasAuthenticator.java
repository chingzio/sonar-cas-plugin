/*
 * Sonar CAS Plugin
 * Copyright (C) 2012 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.plugins.cas;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.jasig.cas.client.validation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.config.Configuration;
import org.sonar.api.security.Authenticator;
import org.sonar.api.security.UserDetails;
import org.sonar.api.server.ServerSide;
import org.sonar.plugins.cas.cas2.P3Cas20ServiceTicketValidator;
import org.sonar.plugins.cas.util.CasAuthenticationException;
import org.sonar.plugins.cas.util.CasRestClient;
import org.sonar.plugins.cas.util.RestAuthenticator;
import org.sonar.plugins.cas.util.SonarCasPropertyNames;

import javax.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Map;

/**
 * The {@link CasAuthenticator} authenticates the user against the cas rest api with the given username and password
 * from the context. After a successful authentication the cas assertions are stored in the request and the
 * {@link UserDetails}, which are set to the request by the {@link CasUserProvider}, are filled from the assertions.
 *
 * @author Sebastian Sdorra, Cloudogu GmbH
 */
@ServerSide
public final class CasAuthenticator extends Authenticator {

    private static final Logger LOG = LoggerFactory.getLogger(RestAuthenticator.class);

    private final Configuration configuration;
    private final CasAttributeSettings attributeSettings;

    private final String casServerUrlPrefix;
    private final String serviceUrl;
    private final String casProtocol;

    CasAuthenticator(Configuration configuration, CasAttributeSettings attributeSettings) {
        this.configuration = configuration;
        this.attributeSettings = attributeSettings;

        casServerUrlPrefix = getCasServerUrlPrefix();
        serviceUrl = getServiceUrl();
        casProtocol = Strings.nullToEmpty(getCasProtocol()).toLowerCase(Locale.ENGLISH);
    }

    private String getCasServerUrlPrefix() {
        return configuration.get(SonarCasPropertyNames.CAS_SERVER_URL_PREFIX.toString()).get();
    }

    private String getServiceUrl() {
        return configuration.get(SonarCasPropertyNames.SONAR_SERVER_URL.toString()).get();
    }

    private String getCasProtocol() {
        return configuration.get(SonarCasPropertyNames.CAS_PROTOCOL.toString()).get();
    }

    private TicketValidator createTicketValidator() {
        TicketValidator validator;
        if ("saml11".equals(casProtocol)) {
            validator = createSaml11TicketValidator();
        } else if ("cas1".equalsIgnoreCase(casProtocol)) {
            validator = createCas10TicketValidator();
        } else if ("cas2".equalsIgnoreCase(casProtocol)) {
            validator = createCas20ServiceTicketValidator();
        } else {
            throw new IllegalStateException("unknown cas protocol ".concat(casProtocol));
        }
        return validator;
    }

    private Saml11TicketValidator createSaml11TicketValidator() {
        /** TODO pass parameters **/
        return new Saml11TicketValidator(getCasServerUrlPrefix());
    }

    private Cas10TicketValidator createCas10TicketValidator() {
        /** TODO pass parameters **/
        return new Cas10TicketValidator(getCasServerUrlPrefix());
    }

    private Cas20ServiceTicketValidator createCas20ServiceTicketValidator() {
        /** TODO pass parameters **/
        return new P3Cas20ServiceTicketValidator(getCasServerUrlPrefix());
    }

    @Override
    public boolean doAuthenticate(Context context) {
        try {
            CasRestClient crc = new CasRestClient(casServerUrlPrefix, serviceUrl);
            String ticket = crc.createServiceTicket(context.getUsername(), context.getPassword());
            Assertion assertion = createTicketValidator().validate(ticket, serviceUrl);
            if (assertion != null) {
                LOG.info("successful authentication via cas rest api");
                // add assertions to request attribute, in order to process groups with the CasGroupsProvider
                context.getRequest().setAttribute(Assertion.class.getName(), assertion);

                populateUserDetails(context.getRequest(), assertion);
                return true;
            } else {
                LOG.warn("ticket validator returned no assertion");
            }
        } catch (CasAuthenticationException ex) {
            LOG.warn("authentication failed", ex);
        } catch (TicketValidationException ex) {
            LOG.warn("ticket validation failed", ex);
        }
        return false;
    }

    private void populateUserDetails(HttpServletRequest request, Assertion assertion) {
        // get user attributes from request, which was previously added by the CasUserProvider
        UserDetails user = (UserDetails) request.getAttribute(UserDetails.class.getName());
        Preconditions.checkState(user != null, "could not find UserDetails in the request");


        // populate user details from assertion
        Map<String,Object> attributes = assertion.getAttributes();

        user.setUserId(assertion.getPrincipal().getName());

        String displayName = attributeSettings.getDisplayName(attributes);
        if (!Strings.isNullOrEmpty(displayName)) {
            user.setName(displayName);
        }

        String email = attributeSettings.getEmail(attributes);
        if (!Strings.isNullOrEmpty(email)) {
            user.setEmail(email);
        }
    }

}
