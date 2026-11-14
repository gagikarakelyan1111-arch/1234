package com.echoabyss;

import java.util.List;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Эхо-Бездна, версия 0.1: механика звука.
 * Громкие действия создают видимую волну, и монстры рядом идут к источнику шума.
 * Тихие действия (шаг, подкрадывание, шерсть под ногами) шума не создают.
 */
public class EchoAbyss implements ModInitializer {
	public static final String MOD_ID = "echoabyss";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// Настройки (позже вынесем в конфиг-файл)
	/** Радиус шума при добыче блока. */
	static final int BREAK_RADIUS = 20;
	/** Радиус шума при добыче шерсти (она глушит звук). */
	static final int WOOL_BREAK_RADIUS = 4;
	/** Радиус шума при беге. */
	static final int SPRINT_RADIUS = 12;
	/** Как часто бег создаёт шум, в тиках (20 тиков = 1 секунда). */
	static final int SPRINT_INTERVAL_TICKS = 20;
	/** Скорость, с которой монстры идут на шум. */
	static final double LURE_SPEED = 1.2;

	@Override
	public void onInitialize() {
		// Добыча блока: громко, шерсть глушит
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel serverLevel) {
				int radius = state.is(BlockTags.WOOL) ? WOOL_BREAK_RADIUS : BREAK_RADIUS;
				makeNoise(serverLevel, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, radius);
			}
		});

		// Бег: шумно, если не стоять на шерсти
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (!player.isSprinting() || player.tickCount % SPRINT_INTERVAL_TICKS != 0) {
					continue;
				}
				if (player.level() instanceof ServerLevel serverLevel) {
					boolean onWool = serverLevel.getBlockState(player.blockPosition().below()).is(BlockTags.WOOL);
					if (!onWool) {
						makeNoise(serverLevel, player.getX(), player.getY(), player.getZ(), SPRINT_RADIUS);
					}
				}
			}
		});

		LOGGER.info("Эхо-Бездна загружена: слушайте тишину.");
	}

	/** Создаёт волну шума: рисует кольца частиц и зовёт монстров в радиусе к источнику. */
	static void makeNoise(ServerLevel level, double x, double y, double z, int radius) {
		spawnRings(level, x, y, z);

		AABB area = new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
		List<Monster> monsters = level.getEntitiesOfClass(Monster.class, area);
		double maxDistSq = (double) radius * radius;
		for (Monster monster : monsters) {
			if (monster.getTarget() == null && monster.distanceToSqr(x, y, z) <= maxDistSq) {
				monster.getNavigation().moveTo(x, y, z, LURE_SPEED);
			}
		}
	}

	private static void spawnRings(ServerLevel level, double x, double y, double z) {
		for (int ring = 1; ring <= 3; ring++) {
			double r = ring * 1.5;
			int points = 12 + ring * 6;
			for (int i = 0; i < points; i++) {
				double angle = 2 * Math.PI * i / points;
				level.sendParticles(ParticleTypes.END_ROD, x + Math.cos(angle) * r, y + 0.1, z + Math.sin(angle) * r, 1, 0, 0, 0, 0);
			}
		}
	}
}
