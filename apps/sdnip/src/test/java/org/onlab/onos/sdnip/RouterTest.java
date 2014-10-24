package org.onlab.onos.sdnip;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.onlab.onos.ApplicationId;
import org.onlab.onos.net.ConnectPoint;
import org.onlab.onos.net.DefaultHost;
import org.onlab.onos.net.DeviceId;
import org.onlab.onos.net.Host;
import org.onlab.onos.net.HostId;
import org.onlab.onos.net.HostLocation;
import org.onlab.onos.net.PortNumber;
import org.onlab.onos.net.flow.DefaultTrafficSelector;
import org.onlab.onos.net.flow.DefaultTrafficTreatment;
import org.onlab.onos.net.flow.TrafficSelector;
import org.onlab.onos.net.flow.TrafficTreatment;
import org.onlab.onos.net.host.HostListener;
import org.onlab.onos.net.host.HostService;
import org.onlab.onos.net.intent.IntentService;
import org.onlab.onos.net.intent.MultiPointToSinglePointIntent;
import org.onlab.onos.net.provider.ProviderId;
import org.onlab.onos.sdnip.config.BgpPeer;
import org.onlab.onos.sdnip.config.Interface;
import org.onlab.onos.sdnip.config.SdnIpConfigService;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.MacAddress;
import org.onlab.packet.VlanId;
import org.onlab.util.TestUtils;
import org.onlab.util.TestUtils.TestUtilsException;

import com.google.common.collect.Sets;

/**
 * This class tests adding a route, updating a route, deleting a route,
 * and adding a route whose next hop is the local BGP speaker.
 */
public class RouterTest {

    private SdnIpConfigService sdnIpConfigService;
    private InterfaceService interfaceService;
    private IntentService intentService;
    private HostService hostService;

    private static final ConnectPoint SW1_ETH1 = new ConnectPoint(
            DeviceId.deviceId("of:0000000000000001"),
            PortNumber.portNumber(1));

    private static final ConnectPoint SW2_ETH1 = new ConnectPoint(
            DeviceId.deviceId("of:0000000000000002"),
            PortNumber.portNumber(1));

    private static final ConnectPoint SW3_ETH1 = new ConnectPoint(
            DeviceId.deviceId("of:0000000000000003"),
            PortNumber.portNumber(1));

    private static final ApplicationId APPID = new ApplicationId() {
        @Override
        public short id() {
            return 1;
        }

        @Override
        public String name() {
            return "SDNIP";
        }
    };

    private Router router;

    @Before
    public void setUp() throws Exception {
        setUpBgpPeers();

        setUpInterfaceService();
        setUpHostService();

        intentService = createMock(IntentService.class);

        router = new Router(APPID, intentService,
                hostService, sdnIpConfigService, interfaceService);
    }

    /**
     * Sets up BGP peers in external networks.
     */
    private void setUpBgpPeers() {

        Map<IpAddress, BgpPeer> peers = new HashMap<>();

        String peerSw1Eth1 = "192.168.10.1";
        peers.put(IpAddress.valueOf(peerSw1Eth1),
                new BgpPeer("00:00:00:00:00:00:00:01", 1, peerSw1Eth1));

        // Two BGP peers are connected to switch 2 port 1.
        String peer1Sw2Eth1 = "192.168.20.1";
        peers.put(IpAddress.valueOf(peer1Sw2Eth1),
                new BgpPeer("00:00:00:00:00:00:00:02", 1, peer1Sw2Eth1));

        String peer2Sw2Eth1 = "192.168.20.2";
        peers.put(IpAddress.valueOf(peer2Sw2Eth1),
                new BgpPeer("00:00:00:00:00:00:00:02", 1, peer2Sw2Eth1));

        sdnIpConfigService = createMock(SdnIpConfigService.class);
        expect(sdnIpConfigService.getBgpPeers()).andReturn(peers).anyTimes();
        replay(sdnIpConfigService);

    }

    /**
     * Sets up logical interfaces, which emulate the configured interfaces
     * in SDN-IP application.
     */
    private void setUpInterfaceService() {
        interfaceService = createMock(InterfaceService.class);

        Set<Interface> interfaces = Sets.newHashSet();

        Interface sw1Eth1 = new Interface(SW1_ETH1,
                Sets.newHashSet(IpPrefix.valueOf("192.168.10.101/24")),
                MacAddress.valueOf("00:00:00:00:00:01"));

        expect(interfaceService.getInterface(SW1_ETH1)).andReturn(sw1Eth1).anyTimes();
        interfaces.add(sw1Eth1);

        Interface sw2Eth1 = new Interface(SW2_ETH1,
                Sets.newHashSet(IpPrefix.valueOf("192.168.20.101/24")),
                MacAddress.valueOf("00:00:00:00:00:02"));

        expect(interfaceService.getInterface(SW2_ETH1)).andReturn(sw2Eth1).anyTimes();
        interfaces.add(sw2Eth1);

        Interface sw3Eth1 = new Interface(SW3_ETH1,
                Sets.newHashSet(IpPrefix.valueOf("192.168.30.101/24")),
                MacAddress.valueOf("00:00:00:00:00:03"));

        expect(interfaceService.getInterface(SW3_ETH1)).andReturn(sw3Eth1).anyTimes();
        interfaces.add(sw3Eth1);

        expect(interfaceService.getInterfaces()).andReturn(interfaces).anyTimes();

        replay(interfaceService);
    }

    /**
     * Sets up the host service with details of some hosts.
     */
    private void setUpHostService() {
        hostService = createMock(HostService.class);

        hostService.addListener(anyObject(HostListener.class));
        expectLastCall().anyTimes();

        IpPrefix host1Address = IpPrefix.valueOf("192.168.10.1/32");
        Host host1 = new DefaultHost(ProviderId.NONE, HostId.NONE,
                MacAddress.valueOf("00:00:00:00:00:01"), VlanId.NONE,
                new HostLocation(SW1_ETH1, 1),
                        Sets.newHashSet(host1Address));

        expect(hostService.getHostsByIp(host1Address))
                .andReturn(Sets.newHashSet(host1)).anyTimes();
        hostService.startMonitoringIp(host1Address.toIpAddress());
        expectLastCall().anyTimes();


        IpPrefix host2Address = IpPrefix.valueOf("192.168.20.1/32");
        Host host2 = new DefaultHost(ProviderId.NONE, HostId.NONE,
                MacAddress.valueOf("00:00:00:00:00:02"), VlanId.NONE,
                new HostLocation(SW2_ETH1, 1),
                        Sets.newHashSet(host2Address));

        expect(hostService.getHostsByIp(host2Address))
                .andReturn(Sets.newHashSet(host2)).anyTimes();
        hostService.startMonitoringIp(host2Address.toIpAddress());
        expectLastCall().anyTimes();


        replay(hostService);
    }

    /**
     * This method tests adding a route entry.
     */
    @Test
    public void testProcessRouteAdd() throws TestUtilsException {
        // Construct a route entry
        RouteEntry routeEntry = new RouteEntry(
                IpPrefix.valueOf("1.1.1.0/24"),
                IpAddress.valueOf("192.168.10.1"));

        // Construct a MultiPointToSinglePointIntent intent
        TrafficSelector.Builder selectorBuilder =
                DefaultTrafficSelector.builder();
        selectorBuilder.matchEthType(Ethernet.TYPE_IPV4).matchIPDst(
                routeEntry.prefix());

        TrafficTreatment.Builder treatmentBuilder =
                DefaultTrafficTreatment.builder();
        treatmentBuilder.setEthDst(MacAddress.valueOf("00:00:00:00:00:01"));

        Set<ConnectPoint> ingressPoints = new HashSet<ConnectPoint>();
        ingressPoints.add(SW2_ETH1);
        ingressPoints.add(SW3_ETH1);

        MultiPointToSinglePointIntent intent =
                new MultiPointToSinglePointIntent(APPID,
                        selectorBuilder.build(), treatmentBuilder.build(),
                        ingressPoints, SW1_ETH1);

        // Set up test expectation
        reset(intentService);
        intentService.submit(intent);
        replay(intentService);

        // Call the processRouteAdd() method in Router class
        router.leaderChanged(true);
        TestUtils.setField(router, "isActivatedLeader", true);
        router.processRouteAdd(routeEntry);

        // Verify
        assertEquals(router.getRoutes().size(), 1);
        assertTrue(router.getRoutes().contains(routeEntry));
        assertEquals(router.getPushedRouteIntents().size(), 1);
        assertEquals(router.getPushedRouteIntents().iterator().next(),
                intent);
        verify(intentService);
    }

    /**
     * This method tests updating a route entry.
     *
     * @throws TestUtilsException
     */
    @Test
    public void testRouteUpdate() throws TestUtilsException {
        // Firstly add a route
        testProcessRouteAdd();

        // Construct the existing route entry
        RouteEntry routeEntry = new RouteEntry(
                IpPrefix.valueOf("1.1.1.0/24"),
                IpAddress.valueOf("192.168.10.1"));

        // Construct the existing MultiPointToSinglePointIntent intent
        TrafficSelector.Builder selectorBuilder =
                DefaultTrafficSelector.builder();
        selectorBuilder.matchEthType(Ethernet.TYPE_IPV4).matchIPDst(
                routeEntry.prefix());

        TrafficTreatment.Builder treatmentBuilder =
                DefaultTrafficTreatment.builder();
        treatmentBuilder.setEthDst(MacAddress.valueOf("00:00:00:00:00:01"));

        Set<ConnectPoint> ingressPoints = new HashSet<ConnectPoint>();
        ingressPoints.add(SW2_ETH1);
        ingressPoints.add(SW3_ETH1);

        MultiPointToSinglePointIntent intent =
                new MultiPointToSinglePointIntent(APPID,
                        selectorBuilder.build(), treatmentBuilder.build(),
                        ingressPoints, SW1_ETH1);

        // Start to construct a new route entry and new intent
        RouteEntry routeEntryUpdate = new RouteEntry(
                IpPrefix.valueOf("1.1.1.0/24"),
                IpAddress.valueOf("192.168.20.1"));

        // Construct a new MultiPointToSinglePointIntent intent
        TrafficSelector.Builder selectorBuilderNew =
                DefaultTrafficSelector.builder();
        selectorBuilderNew.matchEthType(Ethernet.TYPE_IPV4).matchIPDst(
                routeEntryUpdate.prefix());

        TrafficTreatment.Builder treatmentBuilderNew =
                DefaultTrafficTreatment.builder();
        treatmentBuilderNew.setEthDst(MacAddress.valueOf("00:00:00:00:00:02"));


        Set<ConnectPoint> ingressPointsNew = new HashSet<ConnectPoint>();
        ingressPointsNew.add(SW1_ETH1);
        ingressPointsNew.add(SW3_ETH1);

        MultiPointToSinglePointIntent intentNew =
                new MultiPointToSinglePointIntent(APPID,
                        selectorBuilderNew.build(),
                        treatmentBuilderNew.build(),
                        ingressPointsNew, SW2_ETH1);

        // Set up test expectation
        reset(intentService);
        intentService.withdraw(intent);
        intentService.submit(intentNew);
        replay(intentService);

        // Call the processRouteAdd() method in Router class
        router.leaderChanged(true);
        TestUtils.setField(router, "isActivatedLeader", true);
        router.processRouteAdd(routeEntryUpdate);

        // Verify
        assertEquals(router.getRoutes().size(), 1);
        assertTrue(router.getRoutes().contains(routeEntryUpdate));
        assertEquals(router.getPushedRouteIntents().size(), 1);
        assertEquals(router.getPushedRouteIntents().iterator().next(),
                intentNew);
        verify(intentService);
    }

    /**
     * This method tests deleting a route entry.
     */
    @Test
    public void testProcessRouteDelete() throws TestUtilsException {
        // Firstly add a route
        testProcessRouteAdd();

        // Construct the existing route entry
        RouteEntry routeEntry = new RouteEntry(
                IpPrefix.valueOf("1.1.1.0/24"),
                IpAddress.valueOf("192.168.10.1"));

        // Construct the existing MultiPointToSinglePointIntent intent
        TrafficSelector.Builder selectorBuilder =
                DefaultTrafficSelector.builder();
        selectorBuilder.matchEthType(Ethernet.TYPE_IPV4).matchIPDst(
                routeEntry.prefix());

        TrafficTreatment.Builder treatmentBuilder =
                DefaultTrafficTreatment.builder();
        treatmentBuilder.setEthDst(MacAddress.valueOf("00:00:00:00:00:01"));

        Set<ConnectPoint> ingressPoints = new HashSet<ConnectPoint>();
        ingressPoints.add(SW2_ETH1);
        ingressPoints.add(SW3_ETH1);

        MultiPointToSinglePointIntent intent =
                new MultiPointToSinglePointIntent(APPID,
                        selectorBuilder.build(), treatmentBuilder.build(),
                        ingressPoints, SW1_ETH1);

        // Set up expectation
        reset(intentService);
        intentService.withdraw(intent);
        replay(intentService);

        // Call route deleting method in Router class
        router.leaderChanged(true);
        TestUtils.setField(router, "isActivatedLeader", true);
        router.processRouteDelete(routeEntry);

        // Verify
        assertEquals(router.getRoutes().size(), 0);
        assertEquals(router.getPushedRouteIntents().size(), 0);
        verify(intentService);
    }

    /**
     * This method tests when the next hop of a route is the local BGP speaker.
     *
     * @throws TestUtilsException
     */
    @Test
    public void testLocalRouteAdd() throws TestUtilsException {
        // Construct a route entry, the next hop is the local BGP speaker
        RouteEntry routeEntry = new RouteEntry(
                IpPrefix.valueOf("1.1.1.0/24"), IpAddress.valueOf("0.0.0.0"));

        // Reset intentService to check whether the submit method is called
        reset(intentService);
        replay(intentService);

        // Call the processRouteAdd() method in Router class
        router.leaderChanged(true);
        TestUtils.setField(router, "isActivatedLeader", true);
        router.processRouteAdd(routeEntry);

        // Verify
        assertEquals(router.getRoutes().size(), 1);
        assertTrue(router.getRoutes().contains(routeEntry));
        assertEquals(router.getPushedRouteIntents().size(), 0);
        verify(intentService);
    }
}