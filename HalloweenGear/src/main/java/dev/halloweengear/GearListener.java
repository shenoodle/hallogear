package dev.halloweengear;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles set detection, abilities, weapon effects, particles and cooldowns.
 */
public final class GearListener implements Listener {

    private final HalloweenGear plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final NamespacedKey batArrowKey;
    private final Map<UUID, Long> abilityCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> scytheCooldown = new ConcurrentHashMap<>();
    private final Set<UUID> fireRetaliation = ConcurrentHashMap.newKeySet();

    // Passive regen task for Witch full set
    private BukkitRunnable witchRegenTask;

    public GearListener(HalloweenGear plugin) {
        this.plugin = plugin;
        this.batArrowKey = new NamespacedKey(plugin, "bat_arrow");
        startWitchRegen();
    }

    public void shutdown() {
        if (witchRegenTask != null) witchRegenTask.cancel();
        abilityCooldown.clear();
        scytheCooldown.clear();
        fireRetaliation.clear();
    }

    // ------------------------------------------------------------------ set detection

    /** Returns the set id if the player wears all 4 pieces of the same set, else null. */
    public String getFullSet(Player p) {
        PlayerInventory inv = p.getInventory();
        String set = null;
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack piece = inv.getItem(slot);
            String id = plugin.getId(piece);
            if (id == null) return null;
            String pieceSet = id.substring(0, id.lastIndexOf('_'));
            if (set == null) set = pieceSet;
            else if (!set.equals(pieceSet)) return null;
        }
        return set;
    }

    private boolean isOnCooldown(UUID uuid, Map<UUID, Long> map, long seconds) {
        Long until = map.get(uuid);
        if (until == null) return false;
        if (System.currentTimeMillis() < until) return true;
        map.remove(uuid);
        return false;
    }

    private void setCooldown(UUID uuid, Map<UUID, Long> map, long seconds) {
        map.put(uuid, System.currentTimeMillis() + seconds * 1000L);
    }

    // ------------------------------------------------------------------ ability trigger: Sneak + Swap Hands

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!p.isSneaking()) return;
        if (!plugin.getConfig().getBoolean("armor.abilities.enabled", true)) return;

        String set = getFullSet(p);
        if (set == null) return;

        e.setCancelled(true); // consume the swap so items don't actually swap

        UUID uuid = p.getUniqueId();
        if (isOnCooldown(uuid, abilityCooldown, 1)) {
            long left = (abilityCooldown.get(uuid) - System.currentTimeMillis()) / 1000;
            p.sendActionBar(mm.deserialize("<red>Ability cooling down… " + left + "s"));
            return;
        }

        switch (set) {
            case "pumpkin_king" -> hellfireNova(p);
            case "wraith" -> shadowStep(p);
            case "witch" -> hexBurst(p);
            default -> { }
        }
    }

    private void hellfireNova(Player p) {
        var cfg = plugin.getConfig().getConfigurationSection("armor.abilities.pumpkin_king");
        if (cfg == null) return;
        int cd = cfg.getInt("cooldown-seconds", 25);
        double radius = cfg.getDouble("radius", 6.0);
        double damage = cfg.getDouble("damage", 8.0);
        int fireSec = cfg.getInt("fire-seconds", 6);
        int thornsSec = cfg.getInt("thorns-fire-seconds", 4);

        setCooldown(p.getUniqueId(), abilityCooldown, cd);
        fireRetaliation.add(p.getUniqueId());
        // Remove retaliation after duration
        new BukkitRunnable() {
            @Override public void run() { fireRetaliation.remove(p.getUniqueId()); }
        }.runTaskLater(plugin, thornsSec * 20L);

        Location center = p.getLocation();
        World w = center.getWorld();
        if (w == null) return;

        w.playSound(center, Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.6f);
        w.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
        w.spawnParticle(Particle.FLAME, center.clone().add(0, 1, 0), 120, radius / 2, 1, radius / 2, 0.05);
        w.spawnParticle(Particle.LAVA, center.clone().add(0, 1, 0), 40, radius / 3, 0.5, radius / 3, 0);

        for (Entity ent : w.getNearbyEntities(center, radius, radius, radius)) {
            if (!(ent instanceof LivingEntity le) || ent.equals(p) || ent instanceof ArmorStand) continue;
            le.damage(damage, p);
            le.setFireTicks(fireSec * 20);
        }
        p.sendActionBar(mm.deserialize("<#FF7518>Hellfire Nova!"));
    }

    private void shadowStep(Player p) {
        var cfg = plugin.getConfig().getConfigurationSection("armor.abilities.wraith");
        if (cfg == null) return;
        int cd = cfg.getInt("cooldown-seconds", 12);
        double dist = cfg.getDouble("distance", 9.0);
        int invis = cfg.getInt("invisibility-seconds", 3);

        setCooldown(p.getUniqueId(), abilityCooldown, cd);

        Location from = p.getLocation();
        Vector dir = from.getDirection().normalize().multiply(dist);
        Location to = from.clone().add(dir);
        // Simple ground check
        to.setY(from.getY());
        if (to.getBlock().getType().isSolid()) {
            to = from.clone().add(dir.multiply(0.5));
        }

        World w = from.getWorld();
        if (w != null) {
            w.playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
            w.spawnParticle(Particle.SMOKE, from.clone().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.02);
            w.spawnParticle(Particle.SOUL, from.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.01);
        }
        p.teleport(to);
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, invis * 20, 0, false, true));
        if (w != null) {
            w.spawnParticle(Particle.SMOKE, to.clone().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.02);
        }
        p.sendActionBar(mm.deserialize("<#9FB4C7>Shadow Step!"));
    }

    private void hexBurst(Player p) {
        var cfg = plugin.getConfig().getConfigurationSection("armor.abilities.witch");
        if (cfg == null) return;
        int cd = cfg.getInt("cooldown-seconds", 20);
        double radius = cfg.getDouble("radius", 7.0);
        int curseSec = cfg.getInt("curse-seconds", 6);
        int regenSec = cfg.getInt("self-regen-seconds", 6);
        int absSec = cfg.getInt("self-absorption-seconds", 10);

        setCooldown(p.getUniqueId(), abilityCooldown, cd);

        Location center = p.getLocation();
        World w = center.getWorld();
        if (w == null) return;

        w.playSound(center, Sound.ENTITY_WITCH_AMBIENT, 1.2f, 0.7f);
        w.playSound(center, Sound.ENTITY_EVOKER_CAST_SPELL, 0.9f, 1.1f);
        w.spawnParticle(Particle.WITCH, center.clone().add(0, 1, 0), 80, radius / 2, 1, radius / 2, 0.02);
        w.spawnParticle(Particle.ENCHANT, center.clone().add(0, 1, 0), 50, radius / 3, 0.8, radius / 3, 0.5);

        for (Entity ent : w.getNearbyEntities(center, radius, radius, radius)) {
            if (!(ent instanceof LivingEntity le) || ent.equals(p) || ent instanceof ArmorStand) continue;
            le.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, curseSec * 20, 1, false, true));
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, curseSec * 20, 1, false, true));
            le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, curseSec * 20, 0, false, true));
        }

        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, regenSec * 20, 1, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, absSec * 20, 1, false, true));
        p.sendActionBar(mm.deserialize("<#B05CE0>Hex Burst!"));
    }

    // ------------------------------------------------------------------ passive: fire retaliation (Pumpkin King)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMeleeHitWhileRetaliation(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        if (!fireRetaliation.contains(victim.getUniqueId())) return;
        if (!(e.getDamager() instanceof LivingEntity attacker)) return;
        if (attacker.equals(victim)) return;
        int sec = plugin.getConfig().getInt("armor.abilities.pumpkin_king.thorns-fire-seconds", 4);
        attacker.setFireTicks(sec * 20);
        victim.getWorld().spawnParticle(Particle.FLAME, attacker.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.02);
    }

    // ------------------------------------------------------------------ passive: Wraith dodge

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageWraithDodge(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!"wraith".equals(getFullSet(p))) return;
        double chance = plugin.getConfig().getDouble("armor.wraith-dodge-chance", 0.20);
        if (Math.random() >= chance) return;
        e.setCancelled(true);
        p.getWorld().spawnParticle(Particle.SMOKE, p.getLocation().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0.03);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.5f);
        p.sendActionBar(mm.deserialize("<#9FB4C7>Dodged!"));
    }

    // ------------------------------------------------------------------ Witch passive regen

    private void startWitchRegen() {
        witchRegenTask = new BukkitRunnable() {
            @Override
            public void run() {
                double amount = plugin.getConfig().getDouble("armor.witch-heal-amount", 1.5);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!"witch".equals(getFullSet(p))) continue;
                    if (p.getHealth() >= p.getAttribute(Attribute.MAX_HEALTH).getValue()) continue;
                    p.setHealth(Math.min(p.getHealth() + amount,
                            p.getAttribute(Attribute.MAX_HEALTH).getValue()));
                    if (plugin.getConfig().getBoolean("armor.particles", true)) {
                        p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), 2, 0.2, 0.2, 0.2, 0);
                    }
                }
            }
        };
        witchRegenTask.runTaskTimer(plugin, 60L, 60L); // every 3 seconds
    }

    // ------------------------------------------------------------------ Cursed Blade effects

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCursedBladeHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker)) return;
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (!"cursed_blade".equals(plugin.getId(weapon))) return;
        if (!(e.getEntity() instanceof LivingEntity target)) return;

        int wither = plugin.getConfig().getInt("weapons.cursed-blade.wither-seconds", 4);
        int weak = plugin.getConfig().getInt("weapons.cursed-blade.weakness-seconds", 5);
        target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, wither * 20, 1, false, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, weak * 20, 0, false, true));
        target.getWorld().spawnParticle(Particle.SMOKE, target.getLocation().add(0, 1, 0), 12, 0.3, 0.4, 0.3, 0.01);
    }

    // ------------------------------------------------------------------ Reaper Scythe: lifesteal + Soul Slash

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onScytheHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker)) return;
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (!"reaper_scythe".equals(plugin.getId(weapon))) return;
        if (!(e.getEntity() instanceof LivingEntity)) return;

        double chance = plugin.getConfig().getDouble("weapons.reaper-scythe.lifesteal-chance", 0.25);
        double amount = plugin.getConfig().getDouble("weapons.reaper-scythe.lifesteal-amount", 2.0);
        if (Math.random() < chance) {
            double max = attacker.getAttribute(Attribute.MAX_HEALTH).getValue();
            attacker.setHealth(Math.min(attacker.getHealth() + amount, max));
            attacker.getWorld().spawnParticle(Particle.HEART, attacker.getLocation().add(0, 2, 0), 4, 0.3, 0.2, 0.3, 0);
            attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.4f, 1.8f);
        }
    }

    @EventHandler
    public void onScytheRightClick(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!e.getAction().isRightClick()) return;
        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        if (!"reaper_scythe".equals(plugin.getId(item))) return;

        UUID uuid = p.getUniqueId();
        int cd = plugin.getConfig().getInt("weapons.reaper-scythe.slash-cooldown-seconds", 8);
        if (isOnCooldown(uuid, scytheCooldown, cd)) {
            long left = (scytheCooldown.get(uuid) - System.currentTimeMillis()) / 1000;
            p.sendActionBar(mm.deserialize("<red>Soul Slash cooling down… " + left + "s"));
            return;
        }
        setCooldown(uuid, scytheCooldown, cd);

        double radius = plugin.getConfig().getDouble("weapons.reaper-scythe.slash-radius", 4.5);
        double damage = plugin.getConfig().getDouble("weapons.reaper-scythe.slash-damage", 8.0);
        double healPer = plugin.getConfig().getDouble("weapons.reaper-scythe.heal-per-target", 1.0);
        double maxHeal = plugin.getConfig().getDouble("weapons.reaper-scythe.max-heal", 6.0);

        Location center = p.getLocation();
        World w = center.getWorld();
        if (w == null) return;

        w.playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.7f);
        w.playSound(center, Sound.PARTICLE_SOUL_ESCAPE, 1.0f, 0.8f);
        w.spawnParticle(Particle.SWEEP_ATTACK, center.clone().add(0, 1, 0), 8, radius / 3, 0.3, radius / 3, 0);
        w.spawnParticle(Particle.SOUL, center.clone().add(0, 1, 0), 50, radius / 2, 0.6, radius / 2, 0.02);

        double healed = 0;
        for (Entity ent : w.getNearbyEntities(center, radius, radius, radius)) {
            if (!(ent instanceof LivingEntity le) || ent.equals(p) || ent instanceof ArmorStand) continue;
            le.damage(damage, p);
            healed += healPer;
        }
        healed = Math.min(healed, maxHeal);
        if (healed > 0) {
            double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
            p.setHealth(Math.min(p.getHealth() + healed, max));
            p.sendActionBar(mm.deserialize("<#4B0082>Soul Slash! +" + String.format("%.1f", healed) + " HP"));
        } else {
            p.sendActionBar(mm.deserialize("<#4B0082>Soul Slash!"));
        }
    }

    // ------------------------------------------------------------------ Bat Bow

    @EventHandler
    public void onShootBatBow(EntityShootBowEvent e) {
        ItemStack bow = e.getBow();
        if (!"bat_bow".equals(plugin.getId(bow))) return;
        e.getProjectile().getPersistentDataContainer().set(batArrowKey, PersistentDataType.BYTE, (byte) 1);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        Projectile proj = e.getEntity();
        if (!proj.getPersistentDataContainer().has(batArrowKey, PersistentDataType.BYTE)) return;
        Location at = e.getHitEntity() != null
                ? e.getHitEntity().getLocation().add(0, 1, 0)
                : proj.getLocation();
        ProjectileSource src = proj.getShooter();
        Entity shooter = src instanceof Entity en ? en : null;
        proj.remove();
        batSwarm(shooter, at);
    }

    private void batSwarm(Entity shooter, Location loc) {
        var c = plugin.getConfig();
        int count = c.getInt("weapons.bat-bow.bat-count", 6);
        double pulse = c.getDouble("weapons.bat-bow.pulse-damage", 3.0);
        double radius = c.getDouble("weapons.bat-bow.radius", 3.5);
        int seconds = c.getInt("weapons.bat-bow.duration-seconds", 3);
        boolean affectPlayers = c.getBoolean("weapons.bat-bow.affect-players", true);
        World w = loc.getWorld();
        if (w == null) return;

        List<Bat> bats = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Bat bat = w.spawn(loc, Bat.class, b -> {
                b.setInvulnerable(true);
                b.setSilent(true);
                b.setPersistent(false);
                b.setAwake(true);
            });
            bats.add(bat);
        }
        w.playSound(loc, Sound.ENTITY_BAT_TAKEOFF, 1.0f, 0.8f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ >= seconds) {
                    for (Bat b : bats) if (b.isValid()) b.remove();
                    cancel();
                    return;
                }
                for (Entity ent : w.getNearbyEntities(loc, radius, radius, radius)) {
                    if (!(ent instanceof LivingEntity le)) continue;
                    if (ent instanceof Bat || ent instanceof ArmorStand || ent.equals(shooter)) continue;
                    if (ent instanceof Player && !affectPlayers) continue;
                    le.damage(pulse, shooter);
                }
                w.spawnParticle(Particle.SMOKE, loc, 20, radius / 2, 0.5, radius / 2, 0.01);
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // ------------------------------------------------------------------ item protection (fireproof / never despawn)

    @EventHandler
    public void onItemSpawn(org.bukkit.event.entity.ItemSpawnEvent e) {
        ItemStack stack = e.getEntity().getItemStack();
        if (!plugin.isHalloweenItem(stack)) return;
        if (plugin.getConfig().getBoolean("items.fireproof", true)) {
            e.getEntity().setInvulnerable(true);
        }
        if (plugin.getConfig().getBoolean("items.never-despawn", true)) {
            e.getEntity().setUnlimitedLifetime(true);
        }
    }
}
