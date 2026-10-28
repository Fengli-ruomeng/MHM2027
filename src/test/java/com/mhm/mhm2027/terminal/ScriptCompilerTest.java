package com.mhm.mhm2027.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class ScriptCompilerTest {
    @Test
    void findsTheApisAScriptUses() throws Exception {
        Set<ApiSpec> apis = ScriptCompiler.scanApis("""
                function on_hit_taken(a, amount)
                  if me.health() < 5 then me.speed_boost(0.3, 2) end
                  sys.log("hit")
                end
                """, false, ProgramType.RESIDENT, 1);
        assertEquals(Set.of(ApiSpec.ME_HEALTH, ApiSpec.ME_SPEED_BOOST, ApiSpec.SYS_LOG), apis);
    }

    @Test
    void refusesAnApiAboveTheGatewayLevel() {
        InstallException e = assertThrows(InstallException.class, () ->
                ScriptCompiler.scanApis("me.speed_boost(0.3, 2)", false, ProgramType.RESIDENT, 0));
        assertTrue(e.getMessage().contains("LV1"), e.getMessage());
    }

    @Test
    void refusesUnknownApis() {
        assertThrows(InstallException.class, () ->
                ScriptCompiler.scanApis("me.fly()", false, ProgramType.RESIDENT, 11));
    }

    @Test
    void queryProgramsMayNotChangeTheBody() {
        assertThrows(InstallException.class, () ->
                ScriptCompiler.scanApis("me.speed_boost(0.3, 2)", false, ProgramType.QUERY, 11));
    }

    @Test
    void kernelsMayNotUseProgramOnlyApis() throws Exception {
        assertThrows(InstallException.class, () ->
                ScriptCompiler.scanApis("me.health()", true, null, 11));
        assertEquals(Set.of(ApiSpec.SYS_START), ScriptCompiler.scanApis("sys.start('Monitor')", true, null, 0));
    }

    @Test
    void ignoresApisMentionedInComments() throws Exception {
        Set<ApiSpec> apis = ScriptCompiler.scanApis("""
                -- me.speed_boost would need LV1
                --[[ me.damage_boost too
                     and this ]]
                sys.log("ok")
                """, false, ProgramType.RESIDENT, 0);
        assertEquals(Set.of(ApiSpec.SYS_LOG), apis);
    }

    @Test
    void remoteApisAreNotAvailableYet() {
        assertThrows(InstallException.class, () ->
                ScriptCompiler.scanApis("remote.scan(16)", false, ProgramType.RESIDENT, 11));
    }
}
