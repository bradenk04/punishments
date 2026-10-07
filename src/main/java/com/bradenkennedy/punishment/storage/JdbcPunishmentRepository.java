package com.bradenkennedy.punishment.storage;

import com.bradenkennedy.punishment.api.model.*;
import com.bradenkennedy.punishment.storage.model.PunishmentModel;
import com.bradenkennedy.punishment.storage.model.ImportMetadata;
import com.bradenkennedy.punishment.storage.model.PlayerName;
import com.bradenkennedy.punishment.storage.model.NetworkChange;
import com.j256.ormlite.misc.TransactionManager;
import com.j256.ormlite.dao.*;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

/** Shared JDBC storage. Failed writes never report success. */
public class JdbcPunishmentRepository implements PunishmentRepository, AutoCloseable {
    public final ConnectionSource connectionSource;
    protected final Dao<PunishmentModel, UUID> dao;
    public JdbcPunishmentRepository(String url, String username, String password) throws SQLException {
        connectionSource = new JdbcConnectionSource(url, username, password);
        try {
            TableUtils.createTableIfNotExists(connectionSource, PunishmentModel.class);
            TableUtils.createTableIfNotExists(connectionSource, ImportMetadata.class);
            TableUtils.createTableIfNotExists(connectionSource, PlayerName.class);
            TableUtils.createTableIfNotExists(connectionSource, NetworkChange.class);
            dao = DaoManager.createDao(connectionSource, PunishmentModel.class);
        } catch (SQLException failure) {
            try { connectionSource.close(); } catch (Exception close) { failure.addSuppressed(close); }
            throw failure;
        }
    }
    public void rememberName(UUID id, String name) {
        if (name == null || name.isBlank()) return;
        try { DaoManager.<PlayerName, UUID>createDao(connectionSource, PlayerName.class)
                .createOrUpdate(new PlayerName(id, name)); }
        catch (SQLException error) { throw failure(error); }
    }
    public boolean importRecord(PunishmentModel model, ImportMetadata metadata, boolean dryRun) {
        try {
            if (dryRun) return !dao.idExists(model.getUuid());
            return TransactionManager.callInTransaction(connectionSource, () -> {
                if (dao.idExists(model.getUuid())) return false;
                dao.create(model);
                DaoManager.<ImportMetadata, UUID>createDao(connectionSource, ImportMetadata.class).create(metadata);
                rememberName(model.getTarget(), metadata.targetName);
                change(model.getUuid(), model.getTarget(), "IMPORT");
                return true;
            });
        } catch (SQLException error) {
            try { if (dao.idExists(model.getUuid())) return false; } catch (SQLException ignored) {}
            throw failure(error);
        }
    }
    @Override public void create(Punishment punishment) {
        try { TransactionManager.callInTransaction(connectionSource, () -> {
            dao.create(new PunishmentModel(punishment));
            change(punishment.id(), punishment.target(), "ISSUE"); return null;
        }); }
        catch (SQLException failure) { throw failure(failure); }
    }
    /** Import IDs are derived from source and original ID. */
    public boolean importRecord(PunishmentModel model, boolean dryRun) {
        try {
            if (dao.idExists(model.getUuid())) return false;
            if (dryRun) return true;
            try { dao.create(model); return true; }
            catch (SQLException collision) {
                if (dao.idExists(model.getUuid())) return false;
                throw collision;
            }
        } catch (SQLException error) { throw failure(error); }
    }
    @Override public void revoke(UUID id, UUID issuer, String reason, Instant at) {
        try {
            TransactionManager.callInTransaction(connectionSource, () -> {
            var model = dao.queryForId(id);
            if (model == null) return null;
            model.setIsRevoked(true); model.setRevokedBy(issuer);
            model.setRevokedReason(reason); model.setRevokedAt(at); dao.update(model);
            change(id, model.getTarget(), "REVOKE"); return null;
            });
        } catch (SQLException error) { throw failure(error); }
    }
    @Override public Optional<Punishment> findActive(UUID player, PunishmentType type) {
        return findHistory(player).stream().filter(p -> p.type() == type && !p.revoked() && !p.expired())
                .max(Comparator.comparing(p -> p.issuer().issuedAt()));
    }
    @Override public List<Punishment> findHistory(UUID player) {
        try { return dao.queryForEq("target", player).stream().map(PunishmentModel::toPunishment).toList(); }
        catch (SQLException error) { throw failure(error); }
    }
    @Override public List<Punishment> findUnacknowledgedWarnings(UUID player) {
        try {
            return dao.queryBuilder().where().eq("target", player).and().eq("type", PunishmentType.WARN)
                    .and().eq("revoked", false).and().eq("acknowledged", false).query().stream()
                    .map(PunishmentModel::toPunishment).filter(p -> !p.expired()).toList();
        } catch (SQLException error) { throw failure(error); }
    }
    @Override public void acknowledge(UUID id) {
        try {
            var update = dao.updateBuilder();
            update.updateColumnValue("acknowledged", true).where().idEq(id); update.update();
        } catch (SQLException error) { throw failure(error); }
    }
    @Override public void close() throws Exception { connectionSource.close(); }
    public Optional<Punishment> findById(UUID id) {
        try { return Optional.ofNullable(dao.queryForId(id)).map(PunishmentModel::toPunishment); }
        catch (SQLException error) { throw failure(error); }
    }
    public List<NetworkChange> changesSince(long started) {
        try { return DaoManager.<NetworkChange, UUID>createDao(connectionSource, NetworkChange.class)
                .queryBuilder().where().ge("created", started).query(); }
        catch (SQLException error) { throw failure(error); }
    }
    private void change(UUID id, UUID target, String action) throws SQLException {
        DaoManager.<NetworkChange, UUID>createDao(connectionSource, NetworkChange.class)
                .create(new NetworkChange(id, target, action));
    }
    private static IllegalStateException failure(SQLException cause) {
        return new IllegalStateException("Punishment database operation failed", cause);
    }
}
