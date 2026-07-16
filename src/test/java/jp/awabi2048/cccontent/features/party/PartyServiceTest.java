package jp.awabi2048.cccontent.features.party;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.Set;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class PartyServiceTest {
    private static void addMember(PartyService service, UUID partyId, UUID leader, UUID member, long atMillis) {
        service.invite(partyId, leader, member, atMillis);
        service.acceptInvite(member, partyId, atMillis);
    }

    @Test
    void generalMemberLeavesOnlyAfterGraceExpires() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        PartyService service = new PartyService(new PartyConfiguration(3, 1_000L, 1_000L), NoopPartyEventSink.INSTANCE, () -> 0L);
        var party = service.create(leader, "Raid", "", 3, true);
        addMember(service, party.getId(), leader, member, 0L);

        service.scheduleDeparture(member, 1_000L);
        service.processExpiredDepartures(Set.of(leader), 1_999L);
        assertNotNull(service.partyOf(member));
        service.processExpiredDepartures(Set.of(leader), 2_000L);
        assertNull(service.partyOf(member));
    }

    @Test
    void memberReturningWithinGraceCancelsDeparture() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        PartyService service = new PartyService(new PartyConfiguration(3, 1_000L, 1_000L));
        var party = service.create(leader, "Raid", "", 3, true);
        addMember(service, party.getId(), leader, member, 0L);

        service.scheduleDeparture(member, 1_000L);
        assertTrue(service.cancelPendingDeparture(member, 1_999L));
        service.processExpiredDepartures(Set.of(leader), 2_000L);
        assertNotNull(service.partyOf(member));
    }

    @Test
    void expiringLeaderPrefersOnlineMemberThenStableOfflineMember() {
        UUID leader = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        PartyService service = new PartyService(new PartyConfiguration(4, 1_000L, 1_000L));
        var party = service.create(leader, "Raid", "", 4, true);
        addMember(service, party.getId(), leader, first, 0L);
        addMember(service, party.getId(), leader, second, 0L);

        service.scheduleDeparture(leader, 1_000L);
        service.processExpiredDepartures(Set.of(second), 2_000L);
        assertEquals(second, service.get(party.getId()).getLeader());

        UUID nextLeader = UUID.randomUUID();
        UUID offlineCandidate = UUID.randomUUID();
        PartyService secondService = new PartyService(new PartyConfiguration(4, 1_000L, 1_000L));
        var secondParty = secondService.create(nextLeader, "Raid", "", 4, true);
        addMember(secondService, secondParty.getId(), nextLeader, offlineCandidate, 0L);
        secondService.scheduleDeparture(nextLeader, 1_000L);
        secondService.processExpiredDepartures(Set.of(), 2_000L);
        assertEquals(offlineCandidate, secondService.get(secondParty.getId()).getLeader());
    }

    @Test
    void expiringLastMemberDisbandsParty() {
        UUID leader = UUID.randomUUID();
        PartyService service = new PartyService(new PartyConfiguration(2, 1_000L, 1_000L));
        var party = service.create(leader, "Raid");
        service.scheduleDeparture(leader, 1_000L);
        service.processExpiredDepartures(Set.of(), 2_000L);
        assertNull(service.get(party.getId()));
    }

    @Test
    void pendingDepartureSurvivesRestartAndExpiresFromAbsoluteDeadline() throws Exception {
        var file = Files.createTempFile("party-grace", ".yml");
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        var store = new PartyStore(file);
        var first = new PartyService(new PartyConfiguration(3, 1_000L, 1_000L), NoopPartyEventSink.INSTANCE, () -> 1_000L, store);
        var party = first.create(leader, "Raid", "", 3, true);
        addMember(first, party.getId(), leader, member, 1_000L);
        first.scheduleDeparture(member, 2_000L);

        var restored = new PartyService(new PartyConfiguration(3, 1_000L, 1_000L), NoopPartyEventSink.INSTANCE, () -> 2_500L, store);
        assertNotNull(restored.partyOf(member));
        restored.processExpiredDepartures(Set.of(leader), 3_000L);
        assertNull(restored.partyOf(member));
        Files.deleteIfExists(file);
    }

    @Test
    void ordinaryMemberOperationsRemovePendingDeparturesFromStore() throws Exception {
        var leaveFile = Files.createTempFile("party-leave-pending", ".yml");
        UUID leaveLeader = UUID.randomUUID();
        UUID leaveMember = UUID.randomUUID();
        PartyService leaveService = new PartyService(new PartyConfiguration(3, 1_000L, 1_000L), NoopPartyEventSink.INSTANCE, System::currentTimeMillis, new PartyStore(leaveFile));
        var leaveParty = leaveService.create(leaveLeader, "Raid", "", 3, true);
        addMember(leaveService, leaveParty.getId(), leaveLeader, leaveMember, 0L);
        leaveService.scheduleDeparture(leaveMember, 1_000L);
        leaveService.leave(leaveMember);
        assertTrue(new PartyStore(leaveFile).load().getPendingDepartures().isEmpty());

        var kickFile = Files.createTempFile("party-kick-pending", ".yml");
        UUID kickLeader = UUID.randomUUID();
        UUID kickMember = UUID.randomUUID();
        PartyService kickService = new PartyService(new PartyConfiguration(3, 1_000L, 1_000L), NoopPartyEventSink.INSTANCE, System::currentTimeMillis, new PartyStore(kickFile));
        var kickParty = kickService.create(kickLeader, "Raid", "", 3, true);
        addMember(kickService, kickParty.getId(), kickLeader, kickMember, 0L);
        kickService.scheduleDeparture(kickMember, 1_000L);
        kickService.kick(kickParty.getId(), kickLeader, kickMember);
        assertTrue(new PartyStore(kickFile).load().getPendingDepartures().isEmpty());

        var disbandFile = Files.createTempFile("party-disband-pending", ".yml");
        UUID disbandLeader = UUID.randomUUID();
        UUID disbandMember = UUID.randomUUID();
        PartyService disbandService = new PartyService(new PartyConfiguration(3, 1_000L, 1_000L), NoopPartyEventSink.INSTANCE, System::currentTimeMillis, new PartyStore(disbandFile));
        var disbandParty = disbandService.create(disbandLeader, "Raid", "", 3, true);
        addMember(disbandService, disbandParty.getId(), disbandLeader, disbandMember, 0L);
        disbandService.scheduleDeparture(disbandLeader, 1_000L);
        disbandService.scheduleDeparture(disbandMember, 1_000L);
        disbandService.disband(disbandParty.getId(), disbandLeader);
        assertTrue(new PartyStore(disbandFile).load().getPendingDepartures().isEmpty());

        Files.deleteIfExists(leaveFile);
        Files.deleteIfExists(kickFile);
        Files.deleteIfExists(disbandFile);
    }

    @Test
    void lifecycleUsesUuidOnlyAndExpiresInvites() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        PartyService service = new PartyService(new PartyConfiguration(2, 1_000L));
        var party = service.create(leader, "Raid", "desc", 2, true);

        var invite = service.invite(party.getId(), leader, member, 10_000L);
        assertEquals(11_000L, invite.getExpiresAtMillis());
        assertThrows(IllegalArgumentException.class, () -> service.acceptInvite(member, party.getId(), 11_000L));
        service.invite(party.getId(), leader, member, 20_000L);
        var joined = service.acceptInvite(member, party.getId(), 20_500L);
        assertTrue(joined.getMembers().contains(member));
        assertEquals(party.getId(), service.partyOf(member).getId());
    }

    @Test
    void privatePartyAllowsMemberInvitesWithoutBecomingPublic() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID invitee = UUID.randomUUID();
        PartyService service = new PartyService(new PartyConfiguration(3, 1_000L));
        var party = service.create(leader, "Private", "", 3, false);

        service.invite(party.getId(), leader, member, 0L);
        service.acceptInvite(member, party.getId(), 1L);
        assertDoesNotThrow(() -> service.invite(party.getId(), member, invitee, 2L));
        assertTrue(service.recruitingParties().isEmpty());
    }

    @Test
    void recruitingPartiesCanBeListedAndJoinedDirectly() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        PartyService service = new PartyService(new PartyConfiguration(2, 1_000L));
        var party = service.create(leader, "Open", "", 2, true);

        assertEquals(java.util.List.of(party.getId()), service.recruitingParties().stream().map(PartySnapshot::getId).toList());
        service.join(member, party.getId());
        assertTrue(service.recruitingParties().isEmpty());
        assertEquals(party.getId(), service.partyOf(member).getId());
    }

    @Test
    void leaderOperationsAndChatAreEnforced() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        PartyService service = new PartyService(new PartyConfiguration(3, 1_000L));
        var party = service.create(leader, "Raid");
        service.update(party.getId(), leader, null, "updated", true);
        service.invite(party.getId(), leader, member, 0L);
        service.acceptInvite(member, party.getId(), 1L);
        UUID outsider = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> service.kick(party.getId(), member, leader));
        var transferred = service.transferLeadership(party.getId(), leader, member);
        assertEquals(member, transferred.getLeader());
        assertThrows(IllegalArgumentException.class, () -> service.sendChat(party.getId(), outsider, "not a member"));
        assertDoesNotThrow(() -> service.sendChat(party.getId(), member, "ready"));
        service.leave(leader);
        assertFalse(service.partyOf(leader) != null);
    }

    @Test
    void disablePreservesStateAndRejectsFurtherWork() {
        var events = new java.util.ArrayList<PartySnapshot>();
        PartyFeature feature = new PartyFeature(new PartyConfiguration(), new PartyEventSink() {
            @Override
            public void onPartyDisbanded(PartySnapshot party) {
                events.add(party);
            }
        }, System::currentTimeMillis);
        UUID leader = UUID.randomUUID();
        feature.getService().create(leader, "Raid");
        feature.disable();

        assertFalse(feature.isEnabled());
        assertEquals(1, feature.getService().list().size());
        assertEquals(0, events.size());
        assertThrows(IllegalStateException.class, () -> feature.getService().create(UUID.randomUUID(), "Again"));
    }

    @Test
    void yamlStoreRestoresPartiesMembershipAndOnlyLiveInvites() throws Exception {
        var file = Files.createTempFile("party", ".yml");
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID invitee = UUID.randomUUID();
        UUID expired = UUID.randomUUID();
        UUID liveInvitee = UUID.randomUUID();
        var store = new PartyStore(file);
        var first = new PartyService(new PartyConfiguration(4, 1_000L), NoopPartyEventSink.INSTANCE, () -> 10_000L, store);
        var party = first.create(leader, "Raid", "desc", 4, true);
        first.invite(party.getId(), leader, invitee, 10_000L);
        first.invite(party.getId(), leader, expired, 0L);
        first.acceptInvite(invitee, party.getId(), 10_500L);
        first.invite(party.getId(), leader, liveInvitee, 10_000L);
        first.transferLeadership(party.getId(), leader, invitee);
        first.join(member, party.getId());
        first.close();

        var restored = new PartyService(new PartyConfiguration(4, 1_000L), NoopPartyEventSink.INSTANCE, () -> 10_500L, store);
        assertEquals(Set.of(leader, invitee, member), restored.get(party.getId()).getMembers());
        assertEquals(invitee, restored.get(party.getId()).getLeader());
        assertThrows(IllegalStateException.class, () -> restored.acceptInvite(expired, party.getId(), 10_500L));
        assertDoesNotThrow(() -> restored.acceptInvite(liveInvitee, party.getId(), 10_500L));
        Files.deleteIfExists(file);
    }

    @Test
    void yamlStoreRejectsInvalidTypes() throws Exception {
        var file = Files.createTempFile("party-invalid", ".yml");
        Files.writeString(file, "version: 1\nparties:\n  bad:\n    name: 123\n");
        assertThrows(IllegalStateException.class, () -> new PartyStore(file).load());
        Files.deleteIfExists(file);
    }

    @Test
    void invalidConfigurationFailsFast() {
        assertThrows(IllegalArgumentException.class, () -> new PartyConfiguration(0, 1_000L));
        assertThrows(IllegalArgumentException.class, () -> new PartyConfiguration(6, 0L));
    }
}
