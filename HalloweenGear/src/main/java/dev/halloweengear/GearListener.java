package dev.halloweengear;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class GearListener implements Listener {

    private final HalloweenGear plugin;
    private final NamespacedKey batArrowKey;
    private final Map<UUID, Long> scytheCooldown = new HashMap<>();
    private final Map<UUID, Long> abilityCooldown = new HashMap<>();
    private final Map<UUID, String> activeSet = new HashMap<>();
    private long secondCounter = 0;

    public GearListener(HalloweenGear plugin) {
        this.plugin = plugin;
        this.batArrowKey = new NamespacedKey(plugin, "bat_arrow");
    }

    private boolean bonusesOn() {
        return plugin.getConfig().getBoolean("armor.set-bonuses", true);
    }

    // ================================================================== set detection / passives

    /** Returns the set id if the player wears 4 matching pieces, else null. */
    private String fullSet(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] pieces = {inv.getHelmet(), inv.getChestplate(), inv.getLeggings(), inv.getBoots()};
        String set = null;
        for (int i = 0; i < 4; i++) {
            String id = plugin.getId(pieces[i]);
            String suffix = "_" + HalloweenGear.SUFFIX[i];
            if (id == null || !id.endsWith(suffix)) return null;
            String s = id.substring(0, id.length() - suffix.length());
            if (!HalloweenGear.SETS.contains(s)) return null;
            if (set == null) set = s;
            else if (!set.equals(s)) return null;
        }
        return set;
    }

    public void startTasks() {
        new BukkitRunnable() {
            @Override
            public void run() {
                secondCounter++;
                for (Player p : plugin.getServer().getOnlinePlayers()) tickSet(p);
            }
        }.runTaskTimer(plugin, 20L, 20L);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.getConfig().getBoolean("armor.particles", true)) return;
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    String set = activeSet.get(p.getUniqueId());
                    if (set != null && !p.isInvisible()) spawnSetParticles(p, set);
                }
            }
        }.runTaskTimer(plugin, 10L, 6L);
    }

    private void tickSet(Player p) {
        UUID id = p.getUniqueId();
        String set = bonusesOn() ? fullSet(p) : null;
        String prev = activeSet.get(id);
        if (!Objects.equals(prev, set)) {
            if (prev != null) removeEffects(p, prev);
            if (set == null) activeSet.remove(id);
            else activeSet.put(id, set);
        }
        if (set == null) return;

        switch (set) {
            case "pumpkin_king" -> {
                effect(p, PotionEffectType.NIGHT_VISION, 260, 0);
                effect(p, PotionEffectType.FIRE_RESISTANCE, 100, 0);
                effect(p, PotionEffectType.STRENGTH, 100, 1);
                effect(p, PotionEffectType.RESISTANCE, 100, 0);
            }
            case "wraith" -> {
                effect(p, PotionEffectType.SPEED, 100, 1);
                effect(p, PotionEffectType.SLOW_FALLING, 100, 0);
                effect(p, PotionEffectType.JUMP_BOOST, 100, 0);
            }
            case "witch" -> {
                effect(p, PotionEffectType.WATER_BREATHING, 100, 0);
                effect(p, PotionEffectType.HASTE, 100, 1);
                p.removePotionEffect(PotionEffectType.POISON);
                if (secondCounter % 3 == 0) {
                    double amount = plugin.getConfig().getDouble("armor.witch-heal-amount", 1.5);
                    heal(p, amount);
                }
            }
            default -> { }
        }
    }

    private void effect(Player p, PotionEffectType type, int duration, int amplifier) {
        p.addPotionEffect(new PotionEffect(type, duration, amplifier, true, false, true));
    }

    /** Removes only the (ambient) effects this plugin applied, leaving real potions alone. */
    private void removeEffects(Player p, String set) {
        List<PotionEffectType> types = switch (set) {
            case "pumpkin_king" -> List.of(PotionEffectType.NIGHT_VISION, PotionEffectType.FIRE_RESISTANCE,
                    PotionEffectType.STRENGTH, PotionEffectType.RESISTANCE);
            case "wraith" -> List.of(PotionEffectType.SPEED, PotionEffectType.SLOW_FALLING,
                    PotionEffectType.JUMP_BOOST);
            case "witch" -> List.of(PotionEffectType.WATER_BREATHING, PotionEffectType.HASTE);
            default -> List.of();
        };
        for (PotionEffectType t : types) {
            PotionEffect cur = p.getPotionEffect(t);
            if (cur != null && cur.isAmbient()) p.removePotionEffect(t);
        }
    }

    private void heal(LivingEntity le, double amount) {
        if (le.isDead()) return;
        AttributeInstance max = le.getAttribute(Attribute.MAX_HEALTH);
        double cap = max != null ? max.getValue() : 20.0;
        if (le.getHealth() < cap) le.setHealth(Math.min(cap, le.getHealth() + amount));
    }

    private void spawnSetParticles(Player p, String set) {
        World w = p.getWorld();
        Location body = p.getLocation().add(0, 1.0, 0);
        switch (set) {
            case "pumpkin_king" -> {
                w.spawnParticle(Particle.FLAME, body, 2, 0.35, 0.6, 0.35, 0.01);
                w.spawnParticle(Particle.SMOKE, body, 1, 0.3, 0.6, 0.3, 0.0);
            }
            case "wraith" -> {
                w.spawnParticle(Particle.SOUL, body, 2, 0.35, 0.6, 0.35, 0.01);
                w.spawnParticle(Particle.SMOKE, body, 1, 0.3, 0.6, 0.3, 0.0);
            }
            case "witch" -> {
                w.spawnParticle(Particle.WITCH, body, 2, 0.35, 0.6, 0.35, 0.0);
                w.spawnParticle(Particle.ENCHANT, body, 2, 0.4, 0.7, 0.4, 0.2);
            }
            default -> { }
        }
    }

    // ================================================================== set abilities (Sneak + Swap Hands)

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!p.isSneaking() || !bonusesOn()) return;
        if (!plugin.getConfig().getBoolean("armor.abilities.enabled", true)) return;
        String set = fullSet(p);
        if (set == null) return;
        e.setCancelled(true);
        activate(p, set);
    }

    private void activate(Player p, String set) {
        long now = System.currentTimeMillis();
        Long until = abilityCooldown.get(p.getUniqueId());
        if (until != null && until > now) {
            p.sendActionBar(plugin.mm(String.format(Locale.ROOT, "<red>%s recharging: %.1fs",
                    abilityName(set), (until - now) / 1000.0)));
            return;
        }
        boolean used = switch (set) {
            case "pumpkin_king" -> hellfireNova(p);
            case "wraith" -> shadowStep(p);
            case "witch" -> hexBurst(p);
            default -> false;
        };
        if (used) {
            double cd = plugin.getConfig().getDouble("armor.abilities." + set + ".cooldown-seconds", 20.0);
            abilityCooldown.put(p.getUniqueId(), now + (long) (cd * 1000));
        }
    }

    private String abilityName(String set) {
        return switch (set) {
            case "pumpkin_king" -> "Hellfire Nova";
            case "wraith" -> "Shadow Step";
            default -> "Hex Burst";
        };
    }

    private boolean hellfireNova(Player p) {
        var c = plugin.getConfig();
        double radius = c.getDouble("armor.abilities.pumpkin_king.radius", 6.0);
        double damage = c.getDouble("armor.abilities.pumpkin_king.damage", 8.0);
        int fireTicks = c.getInt("armor.abilities.pumpkin_king.fire-seconds", 6) * 20;
        Location center = p.getLocation();
        World w = p.getWorld();

        for (Entity ent : p.getNearbyEntities(radius, radius, radius)) {
            if (!(ent instanceof LivingEntity le) || ent instanceof ArmorStand) continue;
            if (le.getLocation().distanceSquared(center) > radius * radius) continue;
            le.setFireTicks(fireTicks);
            le.damage(damage, p);
            Vector push = le.getLocation().toVector().subtract(center.toVector());
            push.setY(0);
            if (push.lengthSquared() > 1.0E-4) {
                push.normalize().multiply(1.1);
                push.setY(0.45);
                le.setVelocity(push);
            }
        }

        for (int i = 0; i < 40; i++) {
            double a = Math.PI * 2 * i / 40.0;
            double dx = Math.cos(a);
            double dz = Math.sin(a);
            for (double r = 1.0; r <= radius; r += 1.6) {
                w.spawnParticle(Particle.FLAME, center.getX() + dx * r, center.getY() + 0.2,
                        center.getZ() + dz * r, 0, dx * 0.2, 0.05, dz * 0.2, 1.0);
            }
        }
        w.spawnParticle(Particle.EXPLOSION, center.clone().add(0, 1, 0), 1);
        w.spawnParticle(Particle.LAVA, center.clone().add(0, 1, 0), 25, 1.5, 0.5, 1.5, 0);
        w.playSound(center, Sound.ENTITY_BLAZE_SHOOT, 1.4f, 0.6f);
        w.playSound(center, Sound.ITEM_FIRECHARGE_USE, 1.2f, 0.5f);
        return true;
    }

    private boolean shadowStep(Player p) {
        var c = plugin.getConfig();
        double dist = c.getDouble("armor.abilities.wraith.distance", 9.0);
        int invisTicks = c.getInt("armor.abilities.wraith.invisibility-seconds", 3) * 20;
        World w = p.getWorld();
        Location start = p.getLocation();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();

        RayTraceResult hit = w.rayTraceBlocks(eye, dir, dist, FluidCollisionMode.NEVER, true);
        if (hit != null) {
            dist = Math.min(dist, hit.getHitPosition().distance(eye.toVector()) - 1.0);
        }

        Location dest = null;
        for (double d = dist; d >= 1.5; d -= 0.5) {
            Location t = start.clone().add(dir.clone().multiply(d));
            if (t.getBlock().isPassable() && t.clone().add(0, 1, 0).getBlock().isPassable()) {
                dest = t;
                break;
            }
        }
        if (dest == null) {
            p.sendActionBar(plugin.mm("<gray>No room to step through the shadows."));
            return false;
        }

        w.spawnParticle(Particle.SOUL_FIRE_FLAME, start.clone().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.03);
        w.spawnParticle(Particle.LARGE_SMOKE, start.clone().add(0, 1, 0), 15, 0.3, 0.6, 0.3, 0.02);
        w.playSound(start, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.4f);
        p.teleport(dest);
        w.spawnParticle(Particle.SOUL_FIRE_FLAME, dest.clone().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.03);
        w.playSound(dest, Sound.ENTITY_PHANTOM_FLAP, 1.2f, 0.7f);

        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, invisTicks, 0, false, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, invisTicks, 3, false, false, true));
        return true;
    }

    private boolean hexBurst(Player p) {
        var c = plugin.getConfig();
        double radius = c.getDouble("armor.abilities.witch.radius", 7.0);
        int curse = c.getInt("armor.abilities.witch.curse-seconds", 6) * 20;
        int regen = c.getInt("armor.abilities.witch.self-regen-seconds", 6) * 20;
        int absorb = c.getInt("armor.abilities.witch.self-absorption-seconds", 10) * 20;
        World w = p.getWorld();
        Location center = p.getLocation();

        for (Entity ent : p.getNearbyEntities(radius, radius, radius)) {
            if (!(ent instanceof LivingEntity le) || ent instanceof ArmorStand) continue;
            if (le.getLocation().distanceSquared(center) > radius * radius) continue;
            le.addPotionEffect(new PotionEffect(PotionEffectType.POISON, curse, 0));
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, curse, 1));
            le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, curse, 0));
            le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, curse, 0));
            w.spawnParticle(Particle.WITCH, le.getLocation().add(0, le.getHeight() / 2, 0), 15, 0.3, 0.5, 0.3, 0.05);
        }

        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, regen, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, absorb, 1));

        for (int i = 0; i < 36; i++) {
            double a = Math.PI * 2 * i / 36.0;
            w.spawnParticle(Particle.WITCH, center.getX() + Math.cos(a) * radius, center.getY() + 0.3,
                    center.getZ() + Math.sin(a) * radius, 2, 0.1, 0.3, 0.1, 0.02);
        }
        w.spawnParticle(Particle.ENCHANT, center.clone().add(0, 1, 0), 60, 1.0, 1.0, 1.0, 0.5);
        w.playSound(center, Sound.ENTITY_WITCH_CELEBRATE, 1.2f, 0.8f);
        w.playSound(center, Sound.BLOCK_BREWING_STAND_BREW, 1.2f, 0.6f);
        return true;
    }

    // ================================================================== damage handling

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent e) {
        Entity ent = e.getEntity();

        // Fireproof dropped gear.
        if (ent instanceof Item drop) {
            if (!plugin.getConfig().getBoolean("items.fireproof", true)) return;
            if (plugin.getId(drop.getItemStack()) == null) return;
            switch (e.getCause()) {
                case FIRE, FIRE_TICK, LAVA, BLOCK_EXPLOSION, ENTITY_EXPLOSION, CONTACT -> {
                    e.setCancelled(true);
                    drop.setFireTicks(0);
                }
                default -> { }
            }
            return;
        }

        // Wraith dodge.
        if (ent instanceof Player p && bonusesOn()) {
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID) return;
            if (!"wraith".equals(fullSet(p))) return;
            double chance = plugin.getConfig().getDouble("armor.wraith-dodge-chance", 0.20);
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                e.setCancelled(true);
                Location loc = p.getLocation().add(0, 1, 0);
                p.getWorld().spawnParticle(Particle.SOUL, loc, 12, 0.3, 0.5, 0.3, 0.03);
                p.getWorld().playSound(loc, Sound.ENTITY_PHANTOM_FLAP, 0.8f, 1.5f);
                p.sendActionBar(plugin.mm("<#9FB4C7>You phase through the attack..."));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Entity damager = e.getDamager();
        Entity victimEnt = e.getEntity();

        // Pumpkin King: melee attackers catch fire.
        if (victimEnt instanceof Player vp && damager instanceof LivingEntity attacker
                && bonusesOn() && "pumpkin_king".equals(fullSet(vp))) {
            int secs = plugin.getConfig().getInt("armor.abilities.pumpkin_king.thorns-fire-seconds", 4);
            attacker.setFireTicks(secs * 20);
        }

        // Weapon passives.
        if (!(damager instanceof Player p)) return;
        if (!(victimEnt instanceof LivingEntity victim) || victimEnt instanceof ArmorStand) return;
        String id = plugin.getId(p.getInventory().getItemInMainHand());
        if (id == null) return;
        var c = plugin.getConfig();

        switch (id) {
            case "cursed_blade" -> {
                int wither = c.getInt("weapons.cursed-blade.wither-seconds", 4) * 20;
                int weak = c.getInt("weapons.cursed-blade.weakness-seconds", 5) * 20;
                victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, wither, 0));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, weak, 0));
                Location at = victim.getLocation().add(0, victim.getHeight() / 2, 0);
                victim.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, at, 12, 0.3, 0.4, 0.3, 0.02);
                victim.getWorld().playSound(at, Sound.ENTITY_WITHER_HURT, 0.5f, 1.4f);
            }
            case "reaper_scythe" -> {
                double chance = c.getDouble("weapons.reaper-scythe.lifesteal-chance", 0.25);
                double amount = c.getDouble("weapons.reaper-scythe.lifesteal-amount", 2.0);
                if (ThreadLocalRandom.current().nextDouble() < chance) {
                    heal(p, amount);
                    p.getWorld().spawnParticle(Particle.SOUL, p.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.02);
                }
            }
            default -> { }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPotion(EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player p) || !bonusesOn()) return;
        PotionEffect incoming = e.getNewEffect();
        if (incoming == null || !incoming.getType().equals(PotionEffectType.POISON)) return;
        if ("witch".equals(fullSet(p))) e.setCancelled(true);
    }

    // ================================================================== dropped item protection / cleanup

    @EventHandler
    public void onItemDespawn(ItemDespawnEvent e) {
        if (!plugin.getConfig().getBoolean("items.never-despawn", true)) return;
        if (plugin.getId(e.getEntity().getItemStack()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        String prev = activeSet.remove(id);
        if (prev != null) removeEffects(p, prev);
        scytheCooldown.remove(id);
        abilityCooldown.remove(id);
    }

    // ================================================================== Reaper's Scythe: Soul Slash

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!"reaper_scythe".equals(plugin.getId(e.getItem()))) return;
        soulSlash(e.getPlayer());
    }

    private void soulSlash(Player p) {
        var c = plugin.getConfig();
        long now = System.currentTimeMillis();
        Long until = scytheCooldown.get(p.getUniqueId());
        if (until != null && until > now) {
            p.sendActionBar(plugin.mm(String.format(Locale.ROOT, "<red>Soul Slash recharging: %.1fs",
                    (until - now) / 1000.0)));
            return;
        }
        double cd = c.getDouble("weapons.reaper-scythe.slash-cooldown-seconds", 8);
        scytheCooldown.put(p.getUniqueId(), now + (long) (cd * 1000));

        double radius = c.getDouble("weapons.reaper-scythe.slash-radius", 4.5);
        double damage = c.getDouble("weapons.reaper-scythe.slash-damage", 8.0);
        double healPer = c.getDouble("weapons.reaper-scythe.heal-per-target", 1.0);
        double maxHeal = c.getDouble("weapons.reaper-scythe.max-heal", 6.0);

        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        double healed = 0;

        for (Entity ent : p.getNearbyEntities(radius, radius, radius)) {
            if (!(ent instanceof LivingEntity le) || ent instanceof ArmorStand) continue;
            Vector to = le.getLocation().add(0, le.getHeight() / 2, 0).toVector().subtract(eye.toVector());
            if (to.lengthSquared() > radius * radius || to.lengthSquared() < 1.0E-4) continue;
            if (to.clone().normalize().dot(dir) < 0.5) continue;
            le.damage(damage, p);
            if (healed < maxHeal) {
                double h = Math.min(healPer, maxHeal - healed);
                heal(p, h);
                healed += h;
            }
        }

        Location front = eye.clone().add(dir.clone().multiply(2.0));
        p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, front, 6, 1.2, 0.4, 1.2, 0);
        p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, front, 20, 1.0, 0.4, 1.0, 0.02);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.7f);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.5f, 1.6f);
    }

    // ================================================================== Bat Bow

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        ItemStack bow = e.getBow();
        if (!"bat_bow".equals(plugin.getId(bow))) return;
        e.getProjectile().getPersistentDataContainer().set(batArrowKey, PersistentDataType.BYTE, (byte) 1);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        Projectile proj = e.getEntity();
        if (!proj.getPersistentDataContainer().has(batArrowKey, PersistentDataType.BYTE)) return;
        Location at = e.getHitEntity() != null ? e.getHitEntity().getLocation().add(0, 1, 0) : proj.getLocation();
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
                    for (Bat b : bats) b.remove();
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
}
