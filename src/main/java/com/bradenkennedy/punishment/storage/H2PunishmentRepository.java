package com.bradenkennedy.punishment.storage;

import java.io.File;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import com.bradenkennedy.punishment.storage.model.PunishmentModel;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

public class H2PunishmentRepository implements PunishmentRepository {
    public ConnectionSource connectionSource;
    public static Dao<PunishmentModel, UUID> punishmentDao;

    public H2PunishmentRepository(File dataFolder) throws SQLException {
        String dbPath = new File(dataFolder, "punishments").getAbsolutePath();
        String url = "jdbc:h2:" + dbPath;

        this.connectionSource = new JdbcConnectionSource(url);
        H2PunishmentRepository.punishmentDao = DaoManager.createDao(connectionSource, PunishmentModel.class);

        TableUtils.createTableIfNotExists(connectionSource, PunishmentModel.class);
    }

    @Override
    public void create(Punishment punishment) {
        try {
            punishmentDao.create(new PunishmentModel(punishment));
        } catch (SQLException e) {
        }
    }

    @Override
    public void revoke(UUID punishmentId, UUID revokedBy, String reason, Instant atTime) {
        try {
            PunishmentModel model = punishmentDao.queryForId(punishmentId);
            if (model == null) return;
            model.setIsRevoked(true);
            model.setRevokedBy(revokedBy);
            model.setRevokedReason(reason);

            punishmentDao.update(model);
        } catch (SQLException e) {
        }
    }

    @Override
    public Optional<Punishment> findActive(UUID player, PunishmentType type) {
        try {
            Instant now = Instant.now();
            return punishmentDao.queryBuilder()
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
            return Optional.empty();
        }
    }

    @Override
    public List<Punishment> findUnacknowledgedWarnings(UUID player) {
        try {
            return punishmentDao.queryBuilder()
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
            return new ArrayList<>();
        }
    }

    @Override
    public void acknowledge(UUID punishmentId) {
        try {
            var update = punishmentDao.updateBuilder();
            update.updateColumnValue("acknowledged", true).where().idEq(punishmentId);
            update.update();
        } catch (SQLException e) {
        }
    }

    @Override
    public List<Punishment> findHistory(UUID player) {
        try {
            List<PunishmentModel> models = punishmentDao.queryBuilder()
                    .where()
                    .eq("target", player)
                    .query();
            if (models == null || models.isEmpty()) return new ArrayList<>();
            return models.stream().map(PunishmentModel::toPunishment).toList();
        } catch (SQLException e) {
            return new ArrayList<>();
        }
    }
}
