package net.rp.rpessentials.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
//#if forge
//#else
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
//#endif
import net.neoforged.neoforge.client.IArmPoseTransformer;
import net.rp.rpessentials.items.LicenseItem;

/**
 * Third person pose of a player holding a license, like in first person: both arms forward when the
 //#if forge
 //? * other hand is empty, the license arm only otherwise. Added to {@link HumanoidModel.ArmPose} with
 //? * Forge's {@code ArmPose.create}.
 //#else
 * other hand is empty, the license arm only otherwise. Added to {@link HumanoidModel.ArmPose} through
 * {@code META-INF/enumextensions.json}.
 //#endif
 */
public final class LicenseArmPose {

    private LicenseArmPose() {}

    /** Arms raised forward (radians, 0 = hanging). */
    private static final float ARM_FORWARD = -1.0F;
    /** Arms turned inward so the hands meet in front of the chest. */
    private static final float ARM_INWARD = 0.42F;

    //#if forge
    //? public static final HumanoidModel.ArmPose POSE = HumanoidModel.ArmPose.create(
            //? "RPESSENTIALS_LICENSE", true, (IArmPoseTransformer) LicenseArmPose::apply);
//?
    //? /**
     //? * Creates {@link #POSE} (called from the mod constructor, client only).
     //? * <p>
     //? * HumanoidModel switches on the arm pose through a table sized when it is first used: a pose added
     //? * after that (first time a license is held) is out of the table and crashes the game. Created during
     //? * mod loading, it is in the table from the start.
     //? */
    //? public static void init() {}
    //#else
    public static final EnumProxy<HumanoidModel.ArmPose> POSE = new EnumProxy<>(
            HumanoidModel.ArmPose.class, true, (IArmPoseTransformer) LicenseArmPose::apply);
    //#endif

    /** True when the entity holds a license in one hand and nothing in the other (card held with both hands). */
    public static boolean bothHands(LivingEntity entity) {
        boolean main = entity.getMainHandItem().getItem() instanceof LicenseItem;
        boolean off = entity.getOffhandItem().getItem() instanceof LicenseItem;
        return (main && entity.getOffhandItem().isEmpty()) || (off && entity.getMainHandItem().isEmpty());
    }

    private static void apply(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        if (bothHands(entity)) {
            pose(model.rightArm, ARM_FORWARD, -ARM_INWARD);
            pose(model.leftArm, ARM_FORWARD, ARM_INWARD);
        } else {
            boolean right = arm == HumanoidArm.RIGHT;
            pose(right ? model.rightArm : model.leftArm, ARM_FORWARD + 0.15F, right ? -0.2F : 0.2F);
        }
    }

    private static void pose(ModelPart part, float xRot, float yRot) {
        part.xRot = xRot;
        part.yRot = yRot;
        part.zRot = 0.0F;
    }
}
