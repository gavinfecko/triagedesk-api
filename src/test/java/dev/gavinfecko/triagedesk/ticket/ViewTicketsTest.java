package dev.gavinfecko.triagedesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gavinfecko.triagedesk.common.errors.NotFoundException;
import dev.gavinfecko.triagedesk.common.security.Role;
import dev.gavinfecko.triagedesk.support.Api;
import dev.gavinfecko.triagedesk.support.Api.Session;
import dev.gavinfecko.triagedesk.support.ApiTest;
import dev.gavinfecko.triagedesk.ticket.application.TicketService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@ApiTest
class ViewTicketsTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TicketService service;

    Api api;

    @BeforeEach
    void setUp() {
        api = new Api(mvc, jdbc);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    String open(Session who, String title) {
        MvcTestResult result = mvc.post()
                .uri("/api/v1/tickets")
                .header(HttpHeaders.AUTHORIZATION, who.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(Api.json(java.util.Map.of(
                        "title",
                        title,
                        "description",
                        "Details for " + title,
                        "category_id",
                        dev.gavinfecko.triagedesk.support.Reference.EHR.toString(),
                        "priority",
                        "P3_MEDIUM")))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return Api.read(result).get("key").asString();
    }

    MvcTestResult get(Session who, String path) {
        return mvc.get()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, who.bearer())
                .exchange();
    }

    @Test
    void aRequesterListsOnlyTheirOwnTicketsNewestFirst() {
        Session me = api.loginAs(Role.REQUESTER);
        Session other = api.loginAs(Role.REQUESTER);
        String older = open(me, "EHR login loops back to the start page");
        String theirs = open(other, "Somebody else's scanner is offline");
        String newer = open(me, "EHR prints the wrong patient label");

        MvcTestResult list = get(me, "/api/v1/tickets");
        assertThat(list).hasStatusOk();
        assertThat(list).bodyJson().extractingPath("$.items[*].key").asArray().containsExactly(newer, older);
        assertThat(list).bodyJson().extractingPath("$.page.total_elements").isEqualTo(2);
        assertThat(list).bodyJson().extractingPath("$.items[0].category").isEqualTo("EHR");
        assertThat(list).bodyJson().extractingPath("$.items[0].queue").isEqualTo("Clinical Systems");
        assertThat(list).bodyJson().extractingPath("$.items[*].key").asArray().doesNotContain(theirs);
    }

    @Test
    void aRequesterReadsTheirTicketButGets404ForSomeoneElses() {
        Session me = api.loginAs(Role.REQUESTER);
        Session other = api.loginAs(Role.REQUESTER);
        String mine = open(me, "EHR login loops back to the start page");
        String theirs = open(other, "Somebody else's scanner is offline");

        MvcTestResult detail = get(me, "/api/v1/tickets/" + mine);
        assertThat(detail).hasStatusOk();
        assertThat(detail).bodyJson().extractingPath("$.key").isEqualTo(mine);
        assertThat(detail).bodyJson().extractingPath("$.description").asString().startsWith("Details for");
        assertThat(detail).bodyJson().extractingPath("$.assignee").isNull();
        assertThat(detail).bodyJson().doesNotHavePath("$.warnings");

        MvcTestResult forbidden = get(me, "/api/v1/tickets/" + theirs);
        MvcTestResult unknown = get(me, "/api/v1/tickets/HD-999999");
        assertThat(forbidden)
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("/problems/not-found");
        assertThat(unknown).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void agentsAndAdminsSeeEveryonesTickets() {
        Session requester = api.loginAs(Role.REQUESTER);
        String key = open(requester, "Wi-Fi drops in exam room 3");
        for (Role staff : List.of(Role.AGENT, Role.ADMIN)) {
            Session session = api.loginAs(staff);
            assertThat(get(session, "/api/v1/tickets/" + key)).hasStatusOk();
            assertThat(get(session, "/api/v1/tickets?size=100"))
                    .bodyJson()
                    .extractingPath("$.items[*].key")
                    .asArray()
                    .contains(key);
        }
    }

    @Test
    void ownershipIsEnforcedInTheServiceNotJustTheController() {
        Session owner = api.loginAs(Role.REQUESTER);
        String key = open(owner, "Badge reader at the back door is dead");
        Jwt stranger = Jwt.withTokenValue("t")
                .header("alg", "HS256")
                .subject(api.createUser(Role.REQUESTER).toString())
                .claim("role", "REQUESTER")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new JwtAuthenticationToken(stranger, List.of(new SimpleGrantedAuthority("ROLE_REQUESTER"))));
        assertThatThrownBy(() -> service.get(key)).isInstanceOf(NotFoundException.class);
    }
}
