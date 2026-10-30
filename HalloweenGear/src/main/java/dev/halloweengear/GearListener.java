package dev.halloweengear;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class GearListener implements Listener {

    private static final String[] SUFFIX = {"_helmet", "_chestplate", "_leggings", "_boots"};

    private final HalloweenGear plugin;
    private final NamespacedKey batArrowKey;
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Integer> souls = new HashMap<>();
    private final Map<UUID, String> activeSet = new HashMap<>();
    private long secondCounter = 0;

    public GearListener(HalloweenGear plugin) {
        this.plugin = plugin;
        this.batArrowKey = new NamespacedKey(plugin, "bat_arrow");
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    // =====================================================================
    // Cooldown helpers
    // =====================================================================

    private long remaining(String key) {
        Long until = cooldowns.get(key);
        if (until == null) return 0;
        return Math.max(0, until - System.currentTimeMillis());
    }

    private void startCooldown(String key, long seconds) {
        cooldowns.put(key, System.currentTimeMillis() + seconds * 1000L);
    }

    private void sendCooldownMessage(Player p, String name, long leftMillis) {
        p.sendActionBar(plugin.mm("<red>" + name + " recharging: " + String.format("%.1f", leftMillis / 1000.0) + "s"));
    }

    // =====================================================================
    // Armor sets
    // =====================================================================

    /** Returns the set name if the player wears all 4 pieces of the same set, else null. */
    private String fullSet(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] pieces = {inv.getHelmet(), inv.getChestplate(), inv.getLeggings(), inv.getBoots()};
        String found = null;
        for (int i = 0; i < 4; i++) {
            String id = plugin.getId(pieces[i]);
            if (id == null || !id.endsWith(SUFFIX[i])) return null;
            String set = id.substring(0, id.length() - SUFFIX[i].length());
            if (found == null) {
                found = set;
            } else if (!found.equals(set)) {
                return null;
            }
        }
        return found;
    }

    public void startTasks() {
        // Effects: once per second
        new BukkitRunnable() {
            @Override
            public void run() {
                secondCounter++;
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    tickSet(p);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        // Particles: four times per second
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!cfg().getBoolean("armor.particles", true)) return;
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    String set = activeSet.get(p.getUniqueId());
                    if (set == null) continue;
                    spawnSetParticles(p, set);
                }
            }
        }.runTaskTimer(plugin, 10L, 5L);
    }

    private void tickSet(Player p) {
        String set = fullSet(p);
        String prev = activeSet.get(p.getUniqueId());
        if (!Objects.equals(set, prev)) {
            if (prev != null) removeEffects(p, prev);
            if (set == null) {
                activeSet.remove(p.getUniqueId());
            } else {
                activeSet.put(p.getUniqueId(), set);
            }
        }
        if (set == null) return;
        if (!cfg().getBoolean("armor.set-bonuses", true)) return;

        switch (set) {
            case "pumpkin_king" -> {
                effect(p, PotionEffectType.NIGHT_VISION, 260, 0);
                effect(p, PotionEffectType.FIRE_RESISTANCE, 60, 0);
                effect(p, PotionEffectType.STRENGTH, 60, 0);
            }
            case "wraith" -> {
                effect(p, PotionEffectType.SPEED, 60, 1);
                effect(p, PotionEffectType.SLOW_FALLING, 60, 0);
            }
            case "witch" -> {
                effect(p, PotionEffectType.WATER_BREATHING, 60, 0);
                effect(p, PotionEffectType.HASTE, 60, 0);
                if (secondCounter % 3 == 0) {
                    heal(p, cfg().getDouble("armor.witch-heal-amount", 1.0));
                }
            }
            default -> { }
        }
    }

    private void effect(Player p, PotionEffectType type, int duration, int amplifier) {
        p.addPotionEffect(new PotionEffect(type, duration, amplifier, true, false, true));
    }

    private void removeEffects(Player p, String set) {
        switch (set) {
            case "pumpkin_king" -> {
                p.removePotionEffect(PotionEffectType.NIGHT_VISION);
                p.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
                p.removePotionEffect(PotionEffectType.STRENGTH);
            }
            case "wraith" -> {
                p.removePotionEffect(PotionEffectType.SPEED);
                p.removePotionEffect(PotionEffectType.SLOW_FALLING);
            }
            case "witch" -> {
                p.removePotionEffect(PotionEffectType.WATER_BREATHING);
                p.removePotionEffect(PotionEffectType.HASTE);
            }
            default -> { }
        }
    }

    private void spawnSetParticles(Player p, String set) {
        World w = p.getWorld();
        Location body = p.getLocation().add(0, 1.0, 0);
        switch (set) {
            case "pumpkin_king" -> w.spawnParticle(Particle.FLAME, p.getLocation().add(0, 0.2, 0), 2, 0.3, 0.1, 0.3, 0.01);
            case "wraith" -> {
                w.spawnParticle(Particle.SOUL, body, 2, 0.3, 0.5, 0.3, 0.01);
                w.spawnParticle(Particle.SMOKE, body, 2, 0.3, 0.5, 0.3, 0.01);
            }
            case "witch" -> {
                w.spawnParticle(Particle.WITCH, body, 4, 0.3, 0.6, 0.3, 0.0);
                w.spawnParticle(Particle.ENCHANT, body, 3, 0.4, 0.6, 0.4, 0.5);
            }
            default -> { }
        }
    }

    /** Wraith set: chance to dodge incoming attacks. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        switch (e.getCause()) {
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK, PROJECTILE -> { }
            default -> { return; }
        }
        if (!"wraith".equals(fullSet(p))) return;
        double chance = cfg().getDouble("armor.wraith-dodge-chance", 0.15);
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            e.setCancelled(true);
            Location loc = p.getLocation().add(0, 1, 0);
            p.getWorld().spawnParticle(Particle.SOUL, loc, 15, 0.4, 0.6, 0.4, 0.05);
            p.getWorld().playSound(loc, Sound.ENTITY_PHANTOM_FLAP, 1.0f, 1.4f);
            p.sendActionBar(plugin.mm("<#9FB4C7>You phase through the attack..."));
        }
    }

    /** Dropped Halloween items can't be destroyed by fire, lava, explosions, cactus, etc. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamaged(EntityDamageEvent e) {
        if (!cfg().getBoolean("items.fireproof", true)) return;
        if (!(e.getEntity() instanceof Item drop)) return;
        if (plugin.getId(drop.getItemStack()) == null) return;
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID) return;
        e.setCancelled(true);
        drop.setFireTicks(0);
    }

    /** Dropped Halloween items never despawn. */
    @EventHandler(ignoreCancelled = true)
    public void onItemDespawn(ItemDespawnEvent e) {
        if (!cfg().getBoolean("items.never-despawn", true)) return;
        if (plugin.getId(e.getEntity().getItemStack()) == null) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        activeSet.remove(id);
        souls.remove(id);
        String prefix = id.toString();
        cooldowns.keySet().removeIf(k -> k.startsWith(prefix));
    }

    // =====================================================================
    // Cursed Blade + Reaper's Scythe (on-hit effects)
    // =====================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        if (victim instanceof ArmorStand) return;

        String id = plugin.getId(p.getInventory().getItemInMainHand());
        if (id == null) return;

        switch (id) {
            case "cursed_blade" -> {
                int wither = cfg().getInt("weapons.cursed-blade.wither-seconds", 4) * 20;
                int weak = cfg().getInt("weapons.cursed-blade.weakness-seconds", 5) * 20;
                victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, wither, 0));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, weak, 0));
                Location loc = victim.getLocation().add(0, victim.getHeight() / 2, 0);
                victim.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 12, 0.3, 0.4, 0.3, 0.03);
                victim.getWorld().playSound(loc, Sound.ENTITY_WITHER_HURT, 0.5f, 1.6f);

                // Curse spread: infects up to 3 nearby enemies with Wither
                double spread = cfg().getDouble("weapons.cursed-blade.curse-spread-chance", 0.15);
                if (ThreadLocalRandom.current().nextDouble() < spread) {
                    int infected = 0;
                    for (Entity en : victim.getNearbyEntities(4, 4, 4)) {
                        if (infected >= 3) break;
                        if (!(en instanceof LivingEntity le) || le instanceof ArmorStand) continue;
                        if (le.equals(p) || le.equals(victim)) continue;
                        le.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0));
                        le.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, le.getLocation().add(0, 1, 0), 8, 0.3, 0.4, 0.3, 0.02);
                        infected++;
                    }
                }
            }
            case "reaper_scythe" -> {
                double chance = cfg().getDouble("weapons.reaper-scythe.lifesteal-chance", 0.25);
                if (ThreadLocalRandom.current().nextDouble() < chance) {
                    heal(p, cfg().getDouble("weapons.reaper-scythe.lifesteal-amount", 2.0));
                    Location loc = victim.getLocation().add(0, victim.getHeight() / 2, 0);
                    victim.getWorld().spawnParticle(Particle.SOUL, loc, 10, 0.3, 0.4, 0.3, 0.05);
                }
            }
            default -> { }
        }
    }

    // =====================================================================
    // Reaper's Scythe: souls from kills
    // =====================================================================

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        Player killer = dead.getKiller();
        if (killer == null || dead instanceof ArmorStand) return;
        if (!"reaper_scythe".equals(plugin.getId(killer.getInventory().getItemInMainHand()))) return;

        int max = cfg().getInt("weapons.reaper-scythe.max-souls", 5);
        int count = Math.min(max, souls.getOrDefault(killer.getUniqueId(), 0) + 1);
        souls.put(killer.getUniqueId(), count);
        killer.sendActionBar(plugin.mm("<#B91C1C>Souls: " + count + "/" + max));
        dead.getWorld().spawnParticle(Particle.SOUL, dead.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.05);
    }

    // =====================================================================
    // Right-click abilities: Reaper's Scythe (Soul Slash) and Cursed Blade (Cursed Nova)
    // =====================================================================

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        String id = plugin.getId(e.getItem());
        if (id == null) return;
        if (!id.equals("reaper_scythe") && !id.equals("cursed_blade")) return;

        // Let players still open chests, doors, etc. with these in hand
        if (action == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && e.getClickedBlock().getType().isInteractable()) {
            return;
        }

        e.setCancelled(true);
        if (id.equals("reaper_scythe")) {
            soulSlash(e.getPlayer());
        } else {
            cursedNova(e.getPlayer());
        }
    }

    private void soulSlash(Player p) {
        String key = p.getUniqueId() + ":slash";
        long left = remaining(key);
        if (left > 0) {
            sendCooldownMessage(p, "Soul Slash", left);
            return;
        }
        startCooldown(key, cfg().getLong("weapons.reaper-scythe.slash-cooldown-seconds", 8));

        double radius = cfg().getDouble("weapons.reaper-scythe.slash-radius", 4.5);
        double baseDamage = cfg().getDouble("weapons.reaper-scythe.slash-damage", 8.0);
        double soulBonus = cfg().getDouble("weapons.reaper-scythe.soul-bonus-damage", 2.0);
        double healPer = cfg().getDouble("weapons.reaper-scythe.heal-per-target", 1.0);
        double maxHeal = cfg().getDouble("weapons.reaper-scythe.max-heal", 6.0);

        int stored = souls.getOrDefault(p.getUniqueId(), 0);
        double damage = baseDamage + stored * soulBonus;

        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        double healed = stored; // each stored soul heals 1 HP
        int hits = 0;

        for (Entity en : p.getNearbyEntities(radius, radius, radius)) {
            if (!(en instanceof LivingEntity le) || le instanceof ArmorStand) continue;
            Vector to = le.getLocation().add(0, le.getHeight() / 2, 0).toVector().subtract(eye.toVector());
            if (to.lengthSquared() == 0) continue;
            if (to.normalize().dot(dir) < 0.35) continue;
            le.damage(damage, p);
            hits++;
        }
        healed += Math.min(maxHeal, hits * healPer);
        if (healed > 0) heal(p, healed);

        if (stored > 0) {
            souls.remove(p.getUniqueId());
            p.sendActionBar(plugin.mm("<#B91C1C>Unleashed " + stored + " souls!"));
        }

        World w = p.getWorld();
        Location front = eye.clone().add(dir.clone().multiply(1.6));
        w.spawnParticle(Particle.SWEEP_ATTACK, front, 3, 0.6, 0.3, 0.6, 0);
        w.spawnParticle(Particle.SOUL, front, 20 + stored * 6, 0.8, 0.4, 0.8, 0.05);
        w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.6f);
        w.playSound(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.4f, 1.8f);
    }

    private void cursedNova(Player p) {
        String key = p.getUniqueId() + ":nova";
        long left = remaining(key);
        if (left > 0) {
            sendCooldownMessage(p, "Cursed Nova", left);
            return;
        }

        double cost = cfg().getDouble("weapons.cursed-blade.nova-hp-cost", 2.0);
        if (p.getHealth() <= cost + 2.0) {
            p.sendActionBar(plugin.mm("<red>Too weak to pay the blood price."));
            return;
        }
        startCooldown(key, cfg().getLong("weapons.cursed-blade.nova-cooldown-seconds", 10));
        p.setHealth(p.getHealth() - cost);

        double radius = cfg().getDouble("weapons.cursed-blade.nova-radius", 6.0);
        double damage = cfg().getDouble("weapons.cursed-blade.nova-damage", 6.0);

        for (Entity en : p.getNearbyEntities(radius, radius, radius)) {
            if (!(en instanceof LivingEntity le) || le instanceof ArmorStand) continue;
            if (le.getLocation().distanceSquared(p.getLocation()) > radius * radius) continue;
            le.damage(damage, p);
            le.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1));
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
        }

        World w = p.getWorld();
        Location c = p.getLocation().add(0, 0.3, 0);
        for (double r = 1.0; r <= radius; r += 1.0) {
            for (int k = 0; k < 24; k++) {
                double ang = 2 * Math.PI * k / 24.0;
                w.spawnParticle(Particle.SOUL_FIRE_FLAME,
                        c.getX() + Math.cos(ang) * r, c.getY(), c.getZ() + Math.sin(ang) * r,
                        1, 0, 0, 0, 0);
            }
        }
        w.playSound(c, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 0.8f);
        w.playSound(c, Sound.ENTITY_WITHER_SHOOT, 0.6f, 0.7f);
    }

    // =====================================================================
    // Bat Bow
    // =====================================================================

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        ItemStack bow = e.getBow();
        if (!"bat_bow".equals(plugin.getId(bow))) return;

        double force = e.getForce();
        double minForce = cfg().getDouble("weapons.bat-bow.min-force", 0.5);
        double fullForce = cfg().getDouble("weapons.bat-bow.empowered-force", 0.95);
        if (force < minForce) return; // weak shots just fly normally

        byte level = force >= fullForce ? (byte) 2 : (byte) 1;
        e.getProjectile().getPersistentDataContainer().set(batArrowKey, PersistentDataType.BYTE, level);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        Projectile proj = e.getEntity();
        Byte flag = proj.getPersistentDataContainer().get(batArrowKey, PersistentDataType.BYTE);
        if (flag == null) return;
        // Only burst once per arrow
        proj.getPersistentDataContainer().remove(batArrowKey);

        Location loc = e.getHitEntity() != null
                ? e.getHitEntity().getLocation().add(0, 1, 0)
                : proj.getLocation();
        ProjectileSource src = proj.getShooter();
        Player shooter = (src instanceof Player pl) ? pl : null;
        batSwarm(loc, shooter, flag == 2);
    }

    private void batSwarm(Location loc, Player shooter, boolean empowered) {
        World w = loc.getWorld();
        if (w == null) return;

        int count = cfg().getInt("weapons.bat-bow.bat-count", 6);
        double pulseDamage = cfg().getDouble("weapons.bat-bow.pulse-damage", 3.0);
        double radius = cfg().getDouble("weapons.bat-bow.radius", 3.5);
        int durationTicks = cfg().getInt("weapons.bat-bow.duration-seconds", 3) * 20;
        boolean affectPlayers = cfg().getBoolean("weapons.bat-bow.affect-players", true);
        double batHeal = cfg().getDouble("weapons.bat-bow.bat-heal", 1.0);
        int blindTicks = cfg().getInt("weapons.bat-bow.empowered-blind-seconds", 3) * 20;

        if (empowered) {
            count *= 2;
            radius *= 1.4;
        }
        final double finalRadius = radius;

        List<Bat> bats = new ArrayList<>();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            Location l = loc.clone().add((rnd.nextDouble() - 0.5) * 2, rnd.nextDouble() * 1.5, (rnd.nextDouble() - 0.5) * 2);
            Bat bat = w.spawn(l, Bat.class, b -> {
                b.setInvulnerable(true);
                b.setSilent(true);
                b.setPersistent(false);
                b.setAwake(true);
            });
            bats.add(bat);
        }

        w.playSound(loc, Sound.ENTITY_BAT_TAKEOFF, 1.0f, 0.8f);
        w.spawnParticle(Particle.SMOKE, loc, 25, 0.8, 0.5, 0.8, 0.03);

        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                elapsed += 20;
                int hits = 0;
                for (Entity en : w.getNearbyEntities(loc, finalRadius, finalRadius, finalRadius)) {
                    if (!(en instanceof LivingEntity le)) continue;
                    if (le instanceof Bat || le instanceof ArmorStand) continue;
                    if (shooter != null && le.equals(shooter)) continue;
                    if (!affectPlayers && le instanceof Player) continue;
                    if (shooter != null) {
                        le.damage(pulseDamage, shooter);
                    } else {
                        le.damage(pulseDamage);
                    }
                    if (empowered) {
                        le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindTicks, 0));
                    }
                    hits++;
                }
                // Bats feed the shooter
                if (hits > 0 && shooter != null && shooter.isOnline()) {
                    heal(shooter, batHeal);
                }
                w.spawnParticle(Particle.SMOKE, loc, 10, finalRadius / 2, 0.5, finalRadius / 2, 0.02);

                if (elapsed >= durationTicks) {
                    for (Bat b : bats) b.remove();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    // =====================================================================
    // Util
    // =====================================================================

    @SuppressWarnings("deprecation")
    private void heal(LivingEntity entity, double amount) {
        if (entity.isDead()) return;
        double max = entity.getMaxHealth();
        entity.setHealth(Math.min(max, entity.getHealth() + amount));
    }
}
