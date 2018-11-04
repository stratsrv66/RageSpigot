package net.minecraft.server;

import org.bukkit.event.entity.EntityInteractEvent;

public class BlockPressurePlateWeighted extends BlockPressurePlateAbstract {

    public static final BlockStateInteger POWER = BlockStateInteger.of("power", 0, 15);

    private final int weight;

    protected BlockPressurePlateWeighted(Material material, int i) {
        this(material, i, material.r());
    }

    protected BlockPressurePlateWeighted(Material material, int i, MaterialMapColor materialmapcolor) {
        super(material, materialmapcolor);
        this.j(this.blockStateList.getBlockData().set(BlockPressurePlateWeighted.POWER, 0));
        this.weight = i;
    }

    protected int f(World world, BlockPosition blockposition) {
        int i = 0;
        java.util.Iterator iterator = world.a(Entity.class, this.getBoundingBox(blockposition)).iterator();

        while (iterator.hasNext()) {
            Entity entity = (Entity) iterator.next();

            org.bukkit.event.Cancellable cancellable;

            if (entity instanceof EntityHuman) {
                cancellable = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerInteractEvent((EntityHuman) entity, org.bukkit.event.block.Action.PHYSICAL, blockposition, null, null);
            } else {
                cancellable = new EntityInteractEvent(entity.getBukkitEntity(), world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ()));
                world.getServer().getPluginManager().callEvent((EntityInteractEvent) cancellable);
            }

            if (!cancellable.isCancelled()) {
                i++;
            }
        }

        i = Math.min(i, this.weight);

        if (i > 0) {
            float f = (float) Math.min(this.weight, i) / (float) this.weight;

            return MathHelper.f(f * 15.0F);
        } else {
            return 0;
        }
    }

    protected int e(IBlockData iblockdata) {
        return iblockdata.get(BlockPressurePlateWeighted.POWER);
    }

    protected IBlockData a(IBlockData iblockdata, int i) {
        return iblockdata.set(BlockPressurePlateWeighted.POWER, i);
    }

    public int a(World world) {
        return 10;
    }

    public IBlockData fromLegacyData(int i) {
        return this.getBlockData().set(BlockPressurePlateWeighted.POWER, i);
    }

    public int toLegacyData(IBlockData iblockdata) {
        return iblockdata.get(BlockPressurePlateWeighted.POWER);
    }

    public BlockStateList getStateList() {
        return new BlockStateList(this, BlockPressurePlateWeighted.POWER);
    }

}
