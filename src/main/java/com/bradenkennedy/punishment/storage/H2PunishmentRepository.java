package com.bradenkennedy.punishment.storage;

import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import com.bradenkennedy.punishment.storage.model.PunishmentModel;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import java.io.File;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class H2PunishmentRepository implements PunishmentRepository {
    public final ConnectionSource connectionSource;
    private final Dao<PunishmentModel, UUID> punishmentDao;

    public H2PunishmentRepository(File dataFolder) throws SQLException {
        String dbPath = new File(dataFolder, "punishments").getAbsolutePath();
        String url = "jdbc:h2:" + dbPath;

        this.connectionSource = new JdbcConnectionSource(url);
        this.punishmentDao = DaoManager.createDao(connectionSource, PunishmentModel.class);

        TableUtils.createTableIfNotExists(connectionSource, PunishmentModel.class);
    }

    @Override
    public synchronized void create(Punishment punishment) {
        if (isExclusive(punishment.type())
                && findActive(punishment.target(), punishment.type()).isPresent()) return;
        try {
            requireOpen();
            punishmentDao.create(new PunishmentModel(punishment));
        } catch (SQLException e) {
            throw new StorageException("create punishment", e);
        }
    }

    private void requireOpen() throws SQLException {
        if (!connectionSource.isOpen("punishments")) {
            throw new SQLException("Database connection is closed");
        }
    }

    private static boolean isExclusive(PunishmentType type) {
        return switch (type) {
            case BAN, MUTE -> true;
            case WARN, KICK -> false;
        };
    }

    @Override
    public void revoke(UUID punishmentId, UUID revokedBy, String reason, Instant atTime) {
        try {
            requireOpen();
            PunishmentModel model = punishmentDao.queryForId(punishmentId);
            if (model == null) return;
            model.setIsRevoked(true);
            model.setRevokedBy(revokedBy);
            model.setRevokedReason(reason);
            model.setRevokedAt(atTime);

            punishmentDao.update(model);
        } catch (SQLException e) {
            throw new StorageException("revoke punishment", e);
        }
    }

    @Override
    public Optional<Punishment> findActive(UUID player, PunishmentType type) {
        try {
            requireOpen();
            Instant now = Instant.now();
            return punishmentDao
                    .queryBuilder()
                    .where()
                    .eq("target", player)
                    .and()
                    .eq("type", type)
                    .and()
                    .eq("revoked", false)
                    .query()
                    .stream()
                    .map(PunishmentModel::toPunishment)
                    .filter(p -> p.expiry() == null || p.expiry().isAfter(now))
                    .findFirst();
        } catch (SQLException e) {
            throw new StorageException("read active punishments", e);
        }
    }

    @Override
    public List<Punishment> findUnacknowledgedWarnings(UUID player) {
        try {
            requireOpen();
            return punishmentDao
                    .queryBuilder()
                    .where()
                    .eq("target", player)
                    .and()
                    .eq("type", PunishmentType.WARN)
                    .and()
                    .eq("revoked", false)
                    .and()
                    .eq("acknowledged", false)
                    .query()
                    .stream()
                    .map(PunishmentModel::toPunishment)
                    .toList();
        } catch (SQLException e) {
            throw new StorageException("read pending warnings", e);
        }
    }

    @Override
    public void acknowledge(UUID punishmentId) {
        try {
            requireOpen();
            var update = punishmentDao.updateBuilder();
            update.updateColumnValue("acknowledged", true).where().idEq(punishmentId);
            update.update();
        } catch (SQLException e) {
            throw new StorageException("acknowledge warning", e);
        }
    }

    @Override
    public List<Punishment> findHistory(UUID player) {
        try {
            requireOpen();
            List<PunishmentModel> models =
                    punishmentDao.queryBuilder().where().eq("target", player).query();
            if (models == null || models.isEmpty()) return new ArrayList<>();
            return models.stream().map(PunishmentModel::toPunishment).toList();
        } catch (SQLException e) {
            throw new StorageException("read punishment history", e);
        }
    }
}
