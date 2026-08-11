package com.qinggan.travel.family.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RuntimeSchemaIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void seedsSixRolesAndInitialExecutionWithoutActiveDevices() {
        Integer roles = jdbc.queryForObject("""
            select count(*)
            from trip_family_role_binding b
            join trip t on t.id = b.trip_id
            where t.code = 'qinggan-2026-family'
            """, Integer.class);
        assertThat(roles).isEqualTo(6);

        List<String> codes = jdbc.queryForList("""
            select role from trip_family_role_binding b
            join trip t on t.id = b.trip_id
            where t.code = 'qinggan-2026-family'
            order by role
            """, String.class);
        assertThat(codes).containsExactlyInAnyOrder(
            "FATHER", "MOTHER", "OLDER_SISTER", "YOUNGER_BROTHER", "GRANDFATHER", "GRANDMOTHER");

        Integer active = jdbc.queryForObject(
            "select count(*) from trip_family_role_binding where active_device_id is not null", Integer.class);
        assertThat(active).isZero();

        Long revision = jdbc.queryForObject("""
            select e.revision from trip_execution e
            join trip t on t.id = e.trip_id
            where t.code = 'qinggan-2026-family'
            """, Long.class);
        assertThat(revision).isZero();
    }
}
