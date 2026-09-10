package com.github.orgonag.fbclan.lfg;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class RoleTest
{
    @Test public void wildcardsNeverCrossModes()
    {
        assertFalse(Role.TOB_FILL.canFill(Role.TOB_HM_MELEE));
        assertFalse(Role.COX_FILL.canFill(Role.COX_CM_FILL));
        assertFalse(Role.BA_FILL.canFill(Role.COX_MELEE));
        assertTrue(Role.TOB_FILL.canFill(Role.TOB_MELEE));
    }
    @Test public void directionalFreezeRolesStayDirectional()
    {
        assertFalse(Role.TOB_NFRZ.canFill(Role.TOB_SFRZ));
        assertTrue(Role.TOB_FRZ.canFill(Role.TOB_NFRZ));
    }
    @Test(expected=IllegalArgumentException.class) public void excessiveCompositionIsRejected()
    {
        Role.required(Activity.COX, false, 3, Collections.singletonMap(Role.COX_MELEE, 4));
    }
    @Test public void incompatibleHostCannotAdvertiseRemainingSeats()
    {
        assertTrue(Role.open(Activity.TOB, false, 3, Role.tobComposition(3, false), Collections.singletonList(Role.COX_MAGE)).isEmpty());
    }
}
