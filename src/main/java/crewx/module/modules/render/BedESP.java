package crewx.module.modules.render;
import crewx.module.modules.combat.*;
import crewx.module.modules.movement.*;
import crewx.module.modules.render.*;
import crewx.module.modules.player.*;
import crewx.module.modules.misc.*;

import crewx.CrewX;
import crewx.event.EventTarget;
import crewx.event.types.EventType;
import crewx.events.LoadWorldEvent;
import crewx.events.Render3DEvent;
import crewx.events.TickEvent;
import crewx.mixin.IAccessorRenderManager;
import crewx.module.Module;
import crewx.util.RenderUtil;
import crewx.property.properties.*;
import crewx.property.properties.BooleanProperty;
import crewx.property.properties.ModeProperty;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockBed.EnumPartType;
import net.minecraft.init.Blocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

import java.awt.*;
import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArraySet;

public class BedESP extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final CopyOnWriteArraySet<BlockPos> beds = new CopyOnWriteArraySet<>();
    private BlockPos scanCenter;
    private int scanIndex;
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"Default", "Full"});
    public final ModeProperty color = new ModeProperty("color", 0, new String[]{"Custom", "HUD"});
    public final ColorProperty customColor;
    public final PercentProperty opacity;
    public final BooleanProperty outline;
    public final BooleanProperty obsidian;

    private Color getColor() {
        switch (this.color.getValue()) {
            case 0:
                return new Color(this.customColor.getValue());
            case 1:
                return ((HUD) CrewX.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis());
            default:
                return new Color(-1);
        }
    }

    private void drawObsidianBox(AxisAlignedBB axisAlignedBB) {
        if (this.outline.getValue()) {
            RenderUtil.drawBoundingBox(axisAlignedBB, 170, 0, 170, 255, 1.5F);
        }
        RenderUtil.drawFilledBox(axisAlignedBB, 170, 0, 170);
    }

    private void drawObsidian(BlockPos blockPos) {
        if (this.outline.getValue()) {
            RenderUtil.drawBlockBoundingBox(blockPos, 1.0, 170, 0, 170, 255, 1.5F);
        }
        RenderUtil.drawBlockBox(
                blockPos, 1.0, 170, 0, 170
        );
    }

    public BedESP() {
        super("BedESP", false);
        this.customColor = new ColorProperty("custom-color", (int) 8085714755840333141L, () -> this.color.getValue() == 0);
        this.opacity = new PercentProperty("opacity", 25);
        this.outline = new BooleanProperty("outline", false);
        this.obsidian = new BooleanProperty("obsidian", true);
    }

    public double getHeight() {
        return this.mode.getValue() == 1 ? 1.0 : 0.5625;
    }

    @EventTarget
    public void onWorldLoad(LoadWorldEvent event) {
        beds.clear();
        scanCenter = null;
        scanIndex = 0;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE || mc.theWorld == null
                || mc.thePlayer == null) return;
        BlockPos playerPos = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        if (scanCenter == null || scanCenter.distanceSq(playerPos) > 256.0D) {
            scanCenter = playerPos;
            scanIndex = 0;
        }
        // Some injected clients reuse a compiled chunk renderer, so the renderBlock
        // hook never sees beds that were already loaded when BedESP was enabled.
        final int radius = 48, side = radius * 2 + 1, layers = 17;
        final int plane = side * side, total = plane * layers;
        for (int checked = 0; checked < 2048; checked++) {
            int index = scanIndex++;
            if (scanIndex >= total) scanIndex = 0;
            int horizontal = index % plane;
            int x = ((horizontal % side + radius) % side) - radius;
            int z = ((horizontal / side + radius) % side) - radius;
            int y = ((index / plane + 8) % layers) - 8;
            BlockPos pos = scanCenter.add(x, y, z);
            if (!mc.theWorld.isBlockLoaded(pos)) continue;
            IBlockState state = mc.theWorld.getBlockState(pos);
            if (state.getBlock() instanceof BlockBed
                    && state.getValue(BlockBed.PART) == EnumPartType.HEAD) beds.add(pos);
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (this.isEnabled()) {
            RenderUtil.enableRenderState();
            try {
            for (BlockPos blockPos : this.beds) {
                IBlockState state = mc.theWorld.getBlockState(blockPos);
                if (state.getBlock() instanceof BlockBed && state.getValue(BlockBed.PART) == EnumPartType.HEAD) {
                    BlockPos opposite = blockPos.offset(state.getValue(BlockBed.FACING).getOpposite());
                    IBlockState oppositeState = mc.theWorld.getBlockState(opposite);
                    if (oppositeState.getBlock() instanceof BlockBed && oppositeState.getValue(BlockBed.PART) == EnumPartType.FOOT) {
                        if (this.obsidian.getValue()) {
                            for (EnumFacing facing : Arrays.asList(EnumFacing.UP, EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST)) {
                                BlockPos offsetX = blockPos.offset(facing);
                                BlockPos offsetZ = opposite.offset(facing);
                                boolean xObsidian = mc.theWorld.getBlockState(offsetX).getBlock() == Blocks.obsidian;
                                boolean zObsidian = mc.theWorld.getBlockState(offsetZ).getBlock() == Blocks.obsidian;
                                if (xObsidian && zObsidian) {
                                    this.drawObsidianBox(
                                            new AxisAlignedBB(
                                                    Math.min(offsetX.getX(), offsetZ.getX()),
                                                    offsetX.getY(),
                                                    Math.min(offsetX.getZ(), offsetZ.getZ()),
                                                    Math.max((double) offsetX.getX() + 1.0, (double) offsetZ.getX() + 1.0),
                                                    (double) offsetX.getY() + 1.0,
                                                    Math.max((double) offsetX.getZ() + 1.0, (double) offsetZ.getZ() + 1.0)
                                            )
                                                    .offset(
                                                            -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(),
                                                            -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(),
                                                            -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()
                                                    )
                                    );
                                } else if (xObsidian) {
                                    this.drawObsidian(offsetX);
                                } else if (zObsidian) {
                                    this.drawObsidian(offsetZ);
                                }
                            }
                        }
                        AxisAlignedBB aabb = new AxisAlignedBB(
                                Math.min(blockPos.getX(), opposite.getX()),
                                blockPos.getY(),
                                Math.min(blockPos.getZ(), opposite.getZ()),
                                Math.max((double) blockPos.getX() + 1.0, (double) opposite.getX() + 1.0),
                                (double) blockPos.getY() + this.getHeight(),
                                Math.max((double) blockPos.getZ() + 1.0, (double) opposite.getZ() + 1.0)
                        )
                                .offset(
                                        -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(),
                                        -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(),
                                        -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()
                                );
                        Color color = this.getColor();
                        if (this.outline.getValue()) {
                            RenderUtil.drawBoundingBox(aabb, color.getRed(), color.getGreen(), color.getBlue(), 255, 1.5F);
                        }
                        RenderUtil.drawFilledBox(
                                aabb,
                                color.getRed(),
                                color.getGreen(),
                                color.getBlue()
                        );
                    }
                } else {
                    this.beds.remove(blockPos);
                }
            }
            } finally {
            RenderUtil.disableRenderState();
            }
        }
    }

    @Override
    public void onEnabled() {
        scanCenter = null;
        scanIndex = 0;
        if (mc.renderGlobal != null) {
            mc.renderGlobal.loadRenderers();
        }
    }
}
