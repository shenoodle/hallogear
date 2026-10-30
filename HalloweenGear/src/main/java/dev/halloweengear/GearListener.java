package dev.halloweengear;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemEvent;
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
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** All gameplay: set bonuses, armor abilities, weapon effects, fireproof drops. */
public final class GearListener implements Listener {

    private final HalloweenGear plugin;
    private final Gear gear;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final NamespacedKey batArrowKey;

    private final Map<UUID, ArmorSet> activeSet = new HashMap<>();
    private final Map<UUID, Integer> healTicks = new HashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final List<BukkitRunnable> swarms = new ArrayList<>();

    /** True while Soul Slash is dealing damage, so it can't trigger lifesteal on itself. */
    private boolean slashing = false;

    public GearListener(HalloweenGear plugin, Gear gear) {
        this.plugin = plugin;
        this.gear = gear;
        this.batArrowKey = new NamespacedKey(plugin, "bat_arrow");
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    public void shutdown() {
        for (BukkitRunnable r : new ArrayList<>(swarms)) {
            try {
                r.cancel();
            } catch (IllegalStateException ignored) {
                // already cancelled
            }
        }
        swarms.clear();
    }

    // ================================================================== set detection

    /** The set a player is fully wearing (all 4 pieces of the same set), or null. */
    public ArmorSet fullSet(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] pieces = {inv.getHelmet(), inv.getChestplate(), inv.getLeggings(), inv.getBoots()};
        String first = gear.idOf(pieces[0]);
        if (first == null) return null;
        for (ArmorSet set : ArmorSet.values()) {
            if (!first.equals(set.pieceId(0))) continue;
            for (int i = 1; i < 4; i++) {
                if (!set.pieceId(i).equals(gear.idOf(pieces[i]))) return null;
            }
            return set;
        }
        return null;
    }

    // ================================================================== passive set bonuses

    /** Runs once per second for every online player. */
    public void tickArmor() {
        boolean bonuses = cfg().getBoolean("armor.set-bonuses", true);
        boolean particles = cfg().getBoolean("armor.particles", true);

        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            ArmorSet now = bonuses && !p.isDead() ? fullSet(p) : null;
            ArmorSet prev = activeSet.get(id);

            if (prev != now) {
                if (prev != null) strip(p, prev);
                if (now == null) {
                    activeSet.remove(id);
                    healTicks.remove(id);
                } else {
                    activeSet.put(id, now);
                }
            }
            if (now == null) continue;

            applyEffects(p, now);
            if (particles) ambient(p, now);

            if (now == ArmorSet.WITCH) {
                int t = healTicks.merge(id, 1, Integer::sum);
                if (t >= 3) {
                    healTicks.put(id, 0);
                    witchHeal(p);
                }
            }
        }
    }

    private void applyEffects(Player p, ArmorSet set) {
        switch (set) {
            case PUMPKIN_KING -> {
                grant(p, PotionEffectType.NIGHT_VISION, 0, 260);
                grant(p, PotionEffectType.FIRE_RESISTANCE, 0, 60);
                grant(p, PotionEffectType.STRENGTH, 1, 60);
                grant(p, PotionEffectType.RESISTANCE, 0, 60);
            }
            case WRAITH -> {
                grant(p, PotionEffectType.SPEED, 1, 60);
                grant(p, PotionEffectType.JUMP_BOOST, 0, 60);
                grant(p, PotionEffectType.SLOW_FALLING, 0, 60);
            }
            case WITCH -> {
                grant(p, PotionEffectType.WATER_BREATHING, 0, 60);
                grant(p, PotionEffectType.HASTE, 1, 60);
                p.removePotionEffect(PotionEffectType.POISON);
            }
        }
    }

    private PotionEffectType[] effectTypes(ArmorSet set) {
        return switch (set) {
            case PUMPKIN_KING -> new PotionEffectType[]{PotionEffectType.NIGHT_VISION,
                    PotionEffectType.FIRE_RESISTANCE, PotionEffectType.STRENGTH, PotionEffectType.RESISTANCE};
            case WRAITH -> new PotionEffectType[]{PotionEffectType.SPEED,
                    PotionEffectType.JUMP_BOOST, PotionEffectType.SLOW_FALLING};
            case WITCH -> new PotionEffectType[]{PotionEffectType.WATER_BREATHING, PotionEffectType.HASTE};
        };
    }

    /** Applies an effect unless the player already has a stronger one. */
    private void grant(Player p, PotionEffectType type, int amplifier, int ticks) {
        PotionEffect cur = p.getPotionEffect(type);
        if (cur != null && cur.getAmplifier() > amplifier) return;
        p.addPotionEffect(new PotionEffect(type, ticks, amplifier, true, false, true));
    }

    /** Removes the short ambient effects a set granted once it is taken off. */
    private void strip(Player p, ArmorSet set) {
        for (PotionEffectType type : effectTypes(set)) {
            PotionEffect cur = p.getPotionEffect(type);
            if (cur != null && cur.isAmbient() && cur.getDuration() <= 300) p.removePotionEffect(type);
        }
    }

    private void ambient(Player p, ArmorSet set) {
        Location at = p.getLocation().add(0, 1.0, 0);
        World w = p.getWorld();
        switch (set) {
            case PUMPKIN_KING -> w.spawnParticle(Particle.FLAME, at, 4, 0.35, 0.6, 0.35, 0.01);
            case WRAITH -> w.spawnParticle(Particle.SOUL, at, 3, 0.35, 0.6, 0.35, 0.01);
            case WITCH -> w.spawnParticle(Particle.WITCH, at, 5, 0.35, 0.6, 0.35, 0.01);
        }
    }

    private void witchHeal(Player p) {
        double amt = cfg().getDouble("armor.witch-heal-amount", 1.5);
        AttributeInstance maxHp = p.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHp == null ? 20.0 : maxHp.getValue();
        if (p.getHealth() < max) {
            p.setHealth(Math.min(max, p.getHealth() + amt));
            return;
        }
        // Already full: top up the golden hearts instead.
        AttributeInstance absorb = p.getAttribute(Attribute.MAX_ABSORPTION);
        if (absorb == null) return;
        double cap = absorb.getValue();
        if (p.getAbsorptionAmount() < cap) {
            p.setAbsorptionAmount(Math.min(cap, p.getAbsorptionAmount() + amt));
        }
    }

    // ================================================================== cooldowns

    private double remaining(UUID id, String ability) {
        Long until = cooldowns.get(id + ":" + ability);
        if (until == null) return 0;
        return Math.max(0, (until - System.currentTimeMillis()) / 1000.0);
    }

    private void startCooldown(UUID id, String ability, double seconds) {
        cooldowns.put(id + ":" + ability, System.currentTimeMillis() + (long) (seconds * 1000));
    }

    private void recharging(Player p, String name, double secs) {
        p.sendActionBar(mm.deserialize(String.format(java.util.Locale.ROOT, "<red>%s recharging: %.1fs", name, secs)));
    }

    // ================================================================== armor abilities

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemEvent e) {
        Player p = e.getPlayer();
        if (!p.isSneaking()) return;
        if (!cfg().getBoolean("armor.set-bonuses", true) || !cfg().getBoolean("armor.abilities.enabled", true)) return;

        ArmorSet set = fullSet(p);
        if (set == null) return;
        e.setCancelled(true);

        String name = switch (set) {
            case PUMPKIN_KING -> "Hellfire Nova";
            case WRAITH -> "Shadow Step";
            case WITCH -> "Hex Burst";
        };
        double left = remaining(p.getUniqueId(), set.id);
        if (left > 0) {
            recharging(p, name, left);
            return;
        }

        boolean fired = switch (set) {
            case PUMPKIN_KING -> hellfireNova(p);
            case WRAITH -> shadowStep(p);
            case WITCH -> hexBurst(p);
        };
        if (fired) {
            startCooldown(p.getUniqueId(), set.id,
                    cfg().getDouble("armor.abilities." + set.id + ".cooldown-seconds", 15));
        }
    }

    private boolean hellfireNova(Player p) {
        String base = "armor.abilities.pumpkin_king.";
        double radius = cfg().getDouble(base + "radius", 6.0);
        double damage = cfg().getDouble(base + "damage", 8.0);
        int fireTicks = cfg().getInt(base + "fire-seconds", 6) * 20;

        Location center = p.getLocation();
        World w = p.getWorld();
        for (Entity en : p.getNearbyEntities(radius, radius, radius)) {
            if (!(en instanceof LivingEntity le) || en instanceof ArmorStand) continue;
            if (en instanceof Tameable t && t.isTamed()) continue;
            if (le.getLocation().distanceSquared(center) > radius * radius) continue;

            le.setFireTicks(fireTicks);
            le.damage(damage, p);

            Vector kb = le.getLocation().toVector().subtract(center.toVector());
            kb.setY(0);
            if (kb.lengthSquared() < 1.0E-4) kb = new Vector(0, 0, 1);
            kb.normalize().multiply(1.2);
            kb.setY(0.45);
            le.setVelocity(kb);
        }

        Location mid = center.clone().add(0, 1.0, 0);
        w.spawnParticle(Particle.EXPLOSION, mid, 3, 0.8, 0.4, 0.8, 0);
        w.spawnParticle(Particle.FLAME, mid, 160, radius / 3.0, 0.6, radius / 3.0, 0.08);
        w.spawnParticle(Particle.SMOKE, mid, 60, radius / 3.0, 0.6, radius / 3.0, 0.03);
        w.playSound(center, Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.7f);
        w.playSound(center, Sound.ITEM_FIRECHARGE_USE, 1.2f, 0.8f);
        return true;
    }

    private boolean shadowStep(Player p) {
        String base = "armor.abilities.wraith.";
        double distance = cfg().getDouble(base + "distance", 9.0);
        int invisTicks = cfg().getInt(base + "invisibility-seconds", 3) * 20;

        Location start = p.getLocation();
        Vector dir = start.getDirection().normalize();
        Location eye = p.getEyeLocation();

        // How far can we travel before hitting a wall?
        double free = distance;
        var hit = p.getWorld().rayTraceBlocks(eye, dir, distance, FluidCollisionMode.NEVER, true);
        if (hit != null && hit.getHitPosition() != null) {
            free = Math.max(0, hit.getHitPosition().distance(eye.toVector()) - 0.6);
        }

        // Walk back from the far end until we find a spot with room for the player.
        Location dest = null;
        for (double d = free; d >= 1.0; d -= 0.5) {
            Location probe = start.clone().add(dir.clone().multiply(d));
            if (probe.getBlock().isPassable() && probe.clone().add(0, 1, 0).getBlock().isPassable()) {
                dest = probe;
                break;
            }
        }
        if (dest == null) {
            p.sendActionBar(mm.deserialize("<gray>No room to step through the shadows."));
            return false;
        }

        World w = p.getWorld();
        w.spawnParticle(Particle.SOUL_FIRE_FLAME, start.clone().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.04);
        w.playSound(start, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
        p.teleport(dest);
        w.spawnParticle(Particle.SOUL_FIRE_FLAME, dest.clone().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.04);
        w.playSound(dest, Sound.ENTITY_PHANTOM_FLAP, 1.0f, 0.6f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, invisTicks, 0, true, false, true));
        return true;
    }

    private boolean hexBurst(Player p) {
        String base = "armor.abilities.witch.";
        double radius = cfg().getDouble(base + "radius", 7.0);
        int curse = cfg().getInt(base + "curse-seconds", 6) * 20;
        int regen = cfg().getInt(base + "self-regen-seconds", 6) * 20;
        int absorb = cfg().getInt(base + "self-absorption-seconds", 10) * 20;

        Location center = p.getLocation();
        World w = p.getWorld();
        for (Entity en : p.getNearbyEntities(radius, radius, radius)) {
            if (!(en instanceof LivingEntity le) || en instanceof ArmorStand) continue;
            if (en instanceof Tameable t && t.isTamed()) continue;
            if (le.getLocation().distanceSquared(center) > radius * radius) continue;
            le.addPotionEffect(new PotionEffect(PotionEffectType.POISON, curse, 0));
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, curse, 1));
            le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, curse, 0));
            le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, curse, 0));
        }
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, regen, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, absorb, 1));

        Location mid = center.clone().add(0, 1.0, 0);
        w.spawnParticle(Particle.WITCH, mid, 120, radius / 3.0, 0.7, radius / 3.0, 0.05);
        w.spawnParticle(Particle.ENCHANT, mid, 80, radius / 3.0, 0.8, radius / 3.0, 0.5);
        w.playSound(center, Sound.ENTITY_WITCH_CELEBRATE, 1.2f, 0.9f);
        w.playSound(center, Sound.BLOCK_BREWING_STAND_BREW, 1.2f, 0.7f);
        return true;
    }

    // ================================================================== armor passives (damage)

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent e) {
        // Dropped Halloween items shrug off fire, lava and explosions.
        if (e.getEntity() instanceof Item item) {
            if (cfg().getBoolean("items.fireproof", true)
                    && gear.idOf(item.getItemStack()) != null
                    && e.getCause() != EntityDamageEvent.DamageCause.VOID) {
                e.setCancelled(true);
                item.setFireTicks(0);
            }
            return;
        }

        // Wraith set: chance to phase through a hit.
        if (!(e.getEntity() instanceof Player p)) return;
        if (!cfg().getBoolean("armor.set-bonuses", true)) return;
        switch (e.getCause()) {
            case VOID, KILL, SUICIDE -> {
                return;
            }
            default -> { }
        }
        if (fullSet(p) != ArmorSet.WRAITH) return;
        double chance = cfg().getDouble("armor.wraith-dodge-chance", 0.20);
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            e.setCancelled(true);
            p.sendActionBar(mm.deserialize("<#9FB4C7>You phase through the attack..."));
            p.getWorld().spawnParticle(Particle.SOUL, p.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.02);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.8f, 1.4f);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent e) {
        // Pumpkin King thorns: melee attackers burn.
        if (e.getEntity() instanceof Player victim && e.getDamager() instanceof LivingEntity attacker
                && cfg().getBoolean("armor.set-bonuses", true)
                && fullSet(victim) == ArmorSet.PUMPKIN_KING) {
            int secs = cfg().getInt("armor.abilities.pumpkin_king.thorns-fire-seconds", 4);
            attacker.setFireTicks(Math.max(attacker.getFireTicks(), secs * 20));
        }

        // Weapon on-hit effects.
        if (!(e.getDamager() instanceof Player attacker) || !(e.getEntity() instanceof LivingEntity target)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        String id = gear.idOf(attacker.getInventory().getItemInMainHand());
        if (id == null) return;

        if (id.equals(Gear.CURSED_BLADE)) {
            int wither = cfg().getInt("weapons.cursed-blade.wither-seconds", 4) * 20;
            int weak = cfg().getInt("weapons.cursed-blade.weakness-seconds", 5) * 20;
            target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, wither, 0));
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, weak, 0));
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WITHER_HURT, 0.6f, 1.4f);
        } else if (id.equals(Gear.REAPER_SCYTHE) && !slashing) {
            double chance = cfg().getDouble("weapons.reaper-scythe.lifesteal-chance", 0.25);
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                heal(attacker, cfg().getDouble("weapons.reaper-scythe.lifesteal-amount", 2.0));
                attacker.getWorld().spawnParticle(Particle.HEART, attacker.getLocation().add(0, 1.8, 0), 4, 0.3, 0.2, 0.3, 0);
            }
        }
    }

    private void heal(Player p, double amount) {
        AttributeInstance maxHp = p.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHp == null ? 20.0 : maxHp.getValue();
        p.setHealth(Math.min(max, p.getHealth() + amount));
    }

    // ================================================================== Reaper's Scythe: Soul Slash

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        if (!Gear.REAPER_SCYTHE.equals(gear.idOf(p.getInventory().getItemInMainHand()))) return;

        // Let players still open chests, doors, etc. with the scythe in hand.
        if (action == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && e.getClickedBlock().getType().isInteractable() && !p.isSneaking()) {
            return;
        }
        // Stop the hoe from tilling soil.
        e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);

        double left = remaining(p.getUniqueId(), Gear.REAPER_SCYTHE);
        if (left > 0) {
            recharging(p, "Soul Slash", left);
            return;
        }
        soulSlash(p);
        startCooldown(p.getUniqueId(), Gear.REAPER_SCYTHE,
                cfg().getDouble("weapons.reaper-scythe.slash-cooldown-seconds", 8));
    }

    private void soulSlash(Player p) {
        String base = "weapons.reaper-scythe.";
        double damage = cfg().getDouble(base + "slash-damage", 8.0);
        double radius = cfg().getDouble(base + "slash-radius", 4.5);
        double healEach = cfg().getDouble(base + "heal-per-target", 1.0);
        double maxHeal = cfg().getDouble(base + "max-heal", 6.0);

        Location eye = p.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        World w = p.getWorld();

        double healed = 0;
        slashing = true;
        try {
            for (Entity en : p.getNearbyEntities(radius, radius, radius)) {
                if (!(en instanceof LivingEntity le) || en instanceof ArmorStand) continue;
                if (en instanceof Tameable t && t.isTamed()) continue;
                Vector to = le.getEyeLocation().toVector().subtract(eye.toVector());
                if (to.lengthSquared() > radius * radius || to.lengthSquared() < 1.0E-4) continue;
                if (to.clone().normalize().dot(look) < 0.3) continue; // only what is in front of you

                le.damage(damage, p);
                healed = Math.min(maxHeal, healed + healEach);
            }
        } finally {
            slashing = false;
        }
        if (healed > 0) heal(p, healed);

        // A wide arc of sweep particles in front of the player.
        Vector right = look.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1.0E-4) right = new Vector(1, 0, 0);
        right.normalize();
        for (int i = -2; i <= 2; i++) {
            Location pt = eye.clone().add(look.clone().multiply(2.2)).add(right.clone().multiply(i * 0.9));
            w.spawnParticle(Particle.SWEEP_ATTACK, pt, 1, 0, 0, 0, 0);
        }
        w.spawnParticle(Particle.SOUL, eye.clone().add(look.clone().multiply(2.0)), 25, 1.2, 0.4, 1.2, 0.02);
        w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.7f);
        w.playSound(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.6f, 1.5f);
    }

    // ================================================================== Bat Bow

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        ItemStack bow = e.getBow();
        if (bow == null || !Gear.BAT_BOW.equals(gear.idOf(bow))) return;
        e.getProjectile().getPersistentDataContainer().set(batArrowKey, PersistentDataType.BYTE, (byte) 1);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        Projectile proj = e.getEntity();
        if (!proj.getPersistentDataContainer().has(batArrowKey, PersistentDataType.BYTE)) return;

        Location at;
        if (e.getHitEntity() != null) {
            Entity hit = e.getHitEntity();
            at = hit.getLocation().add(0, hit.getHeight() / 2.0, 0);
        } else {
            at = proj.getLocation();
        }
        ProjectileSource src = proj.getShooter();
        LivingEntity shooter = src instanceof LivingEntity le ? le : null;
        proj.remove();
        releaseSwarm(at, shooter);
    }

    private void releaseSwarm(Location at, LivingEntity shooter) {
        String base = "weapons.bat-bow.";
        int count = cfg().getInt(base + "bat-count", 6);
        double pulse = cfg().getDouble(base + "pulse-damage", 3.0);
        double radius = cfg().getDouble(base + "radius", 3.5);
        int seconds = cfg().getInt(base + "duration-seconds", 3);
        boolean hitPlayers = cfg().getBoolean(base + "affect-players", true);

        World w = at.getWorld();
        if (w == null) return;
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        List<Bat> bats = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Bat bat = w.spawn(at, Bat.class, b -> {
                b.setPersistent(false);
                b.setInvulnerable(true);
                b.setAwake(true);
            });
            bat.setVelocity(new Vector(rng.nextDouble(-0.5, 0.5), rng.nextDouble(0.1, 0.5), rng.nextDouble(-0.5, 0.5)));
            bats.add(bat);
        }
        w.playSound(at, Sound.ENTITY_BAT_TAKEOFF, 1.0f, 0.8f);
        w.spawnParticle(Particle.SOUL, at, 30, 0.5, 0.5, 0.5, 0.05);

        BukkitRunnable task = new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (elapsed >= seconds) {
                    finish();
                    return;
                }
                elapsed++;

                for (Entity en : w.getNearbyEntities(at, radius, radius, radius)) {
                    if (!(en instanceof LivingEntity le) || en instanceof Bat || en instanceof ArmorStand) continue;
                    if (le.equals(shooter)) continue;
                    if (le instanceof Player && !hitPlayers) continue;
                    if (shooter != null) le.damage(pulse, shooter);
                    else le.damage(pulse);
                }
                w.spawnParticle(Particle.SOUL, at, 20, radius / 2.0, 0.6, radius / 2.0, 0.02);
                w.playSound(at, Sound.ENTITY_BAT_TAKEOFF, 0.6f, 1.2f);

                // Keep the swarm fluttering around the impact point.
                for (Bat b : bats) {
                    if (!b.isValid()) continue;
                    Vector toward = at.toVector().subtract(b.getLocation().toVector());
                    toward.add(new Vector(rng.nextDouble(-1, 1), rng.nextDouble(-0.2, 0.8), rng.nextDouble(-1, 1)));
                    if (toward.lengthSquared() > 1.0E-4) b.setVelocity(toward.normalize().multiply(0.5));
                }
            }

            private void finish() {
                for (Bat b : bats) b.remove();
                swarms.remove(this);
                cancel();
            }
        };
        swarms.add(task);
        task.runTaskTimer(plugin, 20L, 20L);

        // Remove the bats even if the plugin is reloaded mid-swarm.
        // (shutdown() cancels the task; this makes sure no bats are left behind.)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Bat b : bats) if (b.isValid()) b.remove();
        }, (seconds + 2L) * 20L);
    }

    // ================================================================== dropped items

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent e) {
        Item item = e.getEntity();
        if (gear.idOf(item.getItemStack()) == null) return;
        if (cfg().getBoolean("items.fireproof", true)) item.setInvulnerable(true);
        if (cfg().getBoolean("items.never-despawn", true)) item.setUnlimitedLifetime(true);
    }

    // ================================================================== cleanup

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        activeSet.remove(id);
        healTicks.remove(id);
    }
}
