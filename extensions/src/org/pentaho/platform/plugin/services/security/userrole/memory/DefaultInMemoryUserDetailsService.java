/*!
 * This program is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License, version 2.1 as published by the Free Software
 * Foundation.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this
 * program; if not, you can obtain a copy at http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 * or from the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * Copyright (c) 2002-2016 Pentaho Corporation..  All rights reserved.
 */

package org.pentaho.platform.plugin.services.security.userrole.memory;

import org.pentaho.platform.api.mt.ITenantedPrincipleNameResolver;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Collection;
import java.util.Collections;

public class DefaultInMemoryUserDetailsService extends InMemoryUserDetailsManager {

  ITenantedPrincipleNameResolver userNameUtils;

  public DefaultInMemoryUserDetailsService( ITenantedPrincipleNameResolver userNameUtils ) {
    this( Collections.emptyList() );
    this.userNameUtils = userNameUtils;
  }

  public DefaultInMemoryUserDetailsService( Collection<UserDetails> users ) {
    super( users );
  }

  @Override
  public UserDetails loadUserByUsername( String username ) throws UsernameNotFoundException, DataAccessException {
    return super.loadUserByUsername( userNameUtils.getPrincipleName( username ) );
  }

}
