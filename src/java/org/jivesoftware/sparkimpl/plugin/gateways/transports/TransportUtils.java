/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Lesser Public License (LGPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.sparkimpl.plugin.gateways.transports;

import org.jivesoftware.smack.PacketCollector;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketIDFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.packet.Registration;
import org.jivesoftware.smackx.PrivateDataManager;
import org.jivesoftware.smackx.ServiceDiscoveryManager;
import org.jivesoftware.smackx.packet.DiscoverInfo;
import org.jivesoftware.spark.SparkManager;
import org.jivesoftware.spark.util.log.Log;
import org.jivesoftware.sparkimpl.plugin.gateways.GatewayPrivateData;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles some basic handling of
 */
public class TransportUtils {

    private static Map<String, Transport> transports = new HashMap<String, Transport>();
    private static GatewayPrivateData gatewayPreferences;

    private TransportUtils() {
    }

    static {
        PrivateDataManager.addPrivateDataProvider(GatewayPrivateData.ELEMENT, GatewayPrivateData.NAMESPACE, new GatewayPrivateData.ConferencePrivateDataProvider());

        PrivateDataManager pdm = SparkManager.getSessionManager().getPersonalDataManager();
        try {
            gatewayPreferences = (GatewayPrivateData)pdm.getPrivateData(GatewayPrivateData.ELEMENT, GatewayPrivateData.NAMESPACE);
        }
        catch (XMPPException e) {
            Log.error("Unable to load private data for Gateways", e);
        }
    }

    public static boolean autoJoinService(String serviceName) {
        return gatewayPreferences.autoLogin(serviceName);
    }

    public static void setAutoJoin(String serviceName, boolean autoJoin) {
        gatewayPreferences.addService(serviceName, autoJoin);
        PrivateDataManager pdm = SparkManager.getSessionManager().getPersonalDataManager();
        try {
            pdm.setPrivateData(gatewayPreferences);
        }
        catch (XMPPException e) {
            Log.error(e);
        }
    }

    public static Transport getTransport(String serviceName) {
        // Return transport.
        if (transports.containsKey(serviceName)) {
            return transports.get(serviceName);
        }

        return null;
    }

    public static void addTransport(String serviceName, Transport transport) {
        transports.put(serviceName, transport);
    }

    public static Collection<Transport> getTransports() {
        return transports.values();
    }

    /**
     * Checks if the user is registered with a gateway.
     *
     * @param con       the XMPPConnection.
     * @param transport the transport.
     * @return true if the user is registered with the transport.
     */
    public static boolean isRegistered(XMPPConnection con, Transport transport) {
        ServiceDiscoveryManager discoveryManager = ServiceDiscoveryManager.getInstanceFor(con);
        try {
            DiscoverInfo info = discoveryManager.discoverInfo(transport.getServiceName());
            return info.containsFeature("jabber:iq:registered");
        }
        catch (XMPPException e) {
            Log.error(e);
        }
        return false;
    }

    /**
     * Registers a user with a gateway.
     *
     * @param con           the XMPPConnection.
     * @param gatewayDomain the domain of the gateway (service name)
     * @param username      the username.
     * @param password      the password.
     * @throws XMPPException thrown if there was an issue registering with the gateway.
     */
    public static void registerUser(XMPPConnection con, String gatewayDomain, String username, String password) throws XMPPException {
        Registration registration = new Registration();
        registration.setType(IQ.Type.SET);
        registration.setTo(gatewayDomain);
        registration.addExtension(new GatewayRegisterExtension());

        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put("username", username);
        attributes.put("password", password);
        registration.setAttributes(attributes);

        PacketCollector collector = con.createPacketCollector(new PacketIDFilter(registration.getPacketID()));
        con.sendPacket(registration);

        IQ response = (IQ)collector.nextResult(SmackConfiguration.getPacketReplyTimeout());
        collector.cancel();
        if (response == null) {
            throw new XMPPException("Server timed out");
        }
        if (response.getType() == IQ.Type.ERROR) {
            throw new XMPPException("Error registering user", response.getError());
        }

    }

    /**
     * @param con
     * @param gatewayDomain
     * @throws XMPPException
     */
    public static void unregister(XMPPConnection con, String gatewayDomain) throws XMPPException {
        Registration registration = new Registration();
        registration.setType(IQ.Type.SET);
        registration.setTo(gatewayDomain);
        Map map = new HashMap();
        map.put("remove", "");
        registration.setAttributes(map);


        PacketCollector collector = con.createPacketCollector(new PacketIDFilter(registration.getPacketID()));
        con.sendPacket(registration);

        IQ response = (IQ)collector.nextResult(SmackConfiguration.getPacketReplyTimeout());
        collector.cancel();
        if (response == null) {
            throw new XMPPException("Server timed out");
        }
        if (response.getType() == IQ.Type.ERROR) {
            throw new XMPPException("Error registering user", response.getError());
        }
    }


    static class GatewayRegisterExtension implements PacketExtension {

        public String getElementName() {
            return "x";
        }

        public String getNamespace() {
            return "jabber:iq:gateway:register";
        }

        public String toXML() {
            StringBuilder builder = new StringBuilder();
            builder.append("<").append(getElementName()).append(" xmlns=\"").append(getNamespace()).append(
                    "\"/>");
            return builder.toString();
        }
    }

}