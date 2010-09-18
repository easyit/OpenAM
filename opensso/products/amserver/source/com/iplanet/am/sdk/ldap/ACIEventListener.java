/**
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2005 Sun Microsystems Inc. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * https://opensso.dev.java.net/public/CDDLv1.0.html or
 * opensso/legal/CDDLv1.0.txt
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at opensso/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * $Id: ACIEventListener.java,v 1.5 2009/01/28 05:34:48 ww203982 Exp $
 *
 */

package com.iplanet.am.sdk.ldap;

import com.iplanet.am.sdk.AMEvent;
import com.iplanet.am.sdk.AMObjectListener;
import com.iplanet.am.sdk.common.ICachedDirectoryServices;
import com.iplanet.am.sdk.common.IDirectoryServices;
import com.iplanet.services.ldap.event.DSEvent;
import com.iplanet.services.ldap.event.IDSEventListener;
import com.sun.identity.shared.debug.Debug;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import com.sun.identity.shared.ldap.controls.LDAPPersistSearchControl;

/**
 * The <code>ACIEventListener</code> handles all the events that are generated
 * as a result of changing/deleting/renaming of entries with ACI's. This class
 * implements the <code>com.iplanet.services.ldap.event.IDSEventListener</code>
 * interface.
 */
public class ACIEventListener implements IDSEventListener {

    protected static final String SEARCH_FILTER = "(aci=*)";

    protected static final int OPERATIONS = LDAPPersistSearchControl.ADD
            | LDAPPersistSearchControl.MODIFY | LDAPPersistSearchControl.DELETE
            | LDAPPersistSearchControl.MODDN;

    // Instance variables
    private Debug debug = EventManager.getDebug();

    private Map listeners = new HashMap();

    public ACIEventListener() {
    }

    /**
     * This method will be invoked by the <code>EventService</code> if the
     * events for which this listener registered has been triggered. Since this
     * listener is interested in modifications with respect to ACI's it
     * identifies the DN's affected by this event and sends a notification to
     * the <code>AMObjectImpl</code> by calling the appropriate method.
     * Usually all the DN's whose have a suffix of this DN of this event will
     * get affected
     * <p>
     * 
     * @param dsEvent
     *            <code>DSEvent</code> object generated by the
     *            <code>EventService</code>.
     */
    public void entryChanged(DSEvent dsEvent) {
        if (debug.messageEnabled()) {
            debug.message("ACIEventListener.entryChanged() DSEvent for dn: "
                    + dsEvent.getID());
        }

        // Should not get cos related aci changes events here. But check anyway.
        String objClasses = dsEvent.getClassName();
        if ((objClasses.indexOf("cosClassicDefinition") != -1)
                || (objClasses.indexOf("costemplate") != -1)) {
            return; // Ignore Event.COS entries should'nt contain ACI's
        }

        String affectedDNs = CommonUtils.formatToRFC(dsEvent.getID());
        IDirectoryServices dsServices = DirectoryServicesFactory.getInstance();
        if (DirectoryServicesFactory.isCachingEnabled()) {
            ((ICachedDirectoryServices) dsServices).dirtyCache(affectedDNs,
                dsEvent.getEventType(), false, true,
                Collections.EMPTY_SET);
        }

        // Call Listeners
        synchronized (listeners) {
            Set keys = listeners.keySet();
            for (Iterator items = keys.iterator(); items.hasNext();) {
                AMObjectListener listener = (AMObjectListener) items.next();
                if (dsEvent.getEventType() == DSEvent.OBJECT_CHANGED) {
                    listener.permissionsChanged(dsEvent.getID(),
                        (Map) listeners.get(listener));
                } else {
                    listener.objectChanged(affectedDNs, dsEvent.getEventType(),
                        (Map) listeners.get(listener));
                }
            }
        }
    }

    /**
     * This method is invoked by the <code>EventService</code> if it
     * encounters an error.
     */
    public void eventError(String errorStr) {
        debug.error("ACIEventListener.eventError(): " + errorStr);
    }

    public void allEntriesChanged() {
        // Since for SDK, cache clearing is being done by AMEntryEventListener
        // this method will not do anything.
    }

    public String getBase() {
        return EventManager.EVENT_BASE_NODE;
    }

    public String getFilter() {
        return SEARCH_FILTER;
    }

    public int getOperations() {
        return OPERATIONS;
    }

    public int getScope() {
        return EventManager.EVENT_SCOPE;
    }

    public void setListeners(Map listener) {
        this.listeners = listener;
    }

}
