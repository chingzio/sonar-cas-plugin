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
package org.sonar.plugins.cas.cas2;

import com.google.common.annotations.VisibleForTesting;
import org.jasig.cas.client.validation.Cas20ProxyReceivingTicketValidationFilter;
import org.sonar.api.config.Configuration;
import org.sonar.plugins.cas.util.AbstractCasFilter;

import javax.servlet.Filter;
import java.util.Map;

public final class Cas2ValidationFilter extends AbstractCasFilter {

  public Cas2ValidationFilter(Configuration configuration) {
    this(configuration, new Cas20ProxyReceivingTicketValidationFilter());
  }

  @VisibleForTesting
  Cas2ValidationFilter(Configuration configuration, Filter casFilter) {
    super(configuration, casFilter);
  }

  @Override
  public UrlPattern doGetPattern() {
    return UrlPattern.create("/cas/validate");
  }

  @Override
  protected void doCompleteProperties(Configuration configuration, Map<String, String> properties) {
    properties.put("ticketValidatorClass", P3Cas20ServiceTicketValidator.class.getName());
    properties.put("casServerUrlPrefix", configuration.get("sonar.cas.casServerUrlPrefix").get());
    properties.put("gateway", configuration.get("sonar.cas.sendGateway").orElse("false"));
    properties.put("redirectAfterValidation", "false");
    properties.put("useSession", "false");
    properties.put("exceptionOnValidationFailure", "true");
  }

}
