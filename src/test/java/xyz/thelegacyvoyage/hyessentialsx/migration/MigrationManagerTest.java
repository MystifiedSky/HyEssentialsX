package xyz.thelegacyvoyage.hyessentialsx.migration;

import org.junit.jupiter.api.Test;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MigrationManagerTest {

    @Test
    void mergeConvertsWholeImportedUnitsToStoredScale() {
        PlayerDataModel data = new PlayerDataModel();
        data.setBalance(1_000L);
        data.setBalanceScale(2);

        MigrationManager.applyImportedBalance(data, 10L, true);

        assertEquals(2_000L, data.getBalance());
        assertEquals(2, data.getBalanceScale());
    }

    @Test
    void replaceClearsOldScaleForLaterNormalization() {
        PlayerDataModel data = new PlayerDataModel();
        data.setBalance(1_000L);
        data.setBalanceScale(2);

        MigrationManager.applyImportedBalance(data, 10L, false);

        assertEquals(10L, data.getBalance());
        assertNull(data.getBalanceScale());
    }

    @Test
    void mergeSaturatesInsteadOfWrappingOnOverflow() {
        PlayerDataModel data = new PlayerDataModel();
        data.setBalance(Long.MAX_VALUE - 5L);
        data.setBalanceScale(0);

        MigrationManager.applyImportedBalance(data, 10L, true);

        assertEquals(Long.MAX_VALUE, data.getBalance());
    }
}
