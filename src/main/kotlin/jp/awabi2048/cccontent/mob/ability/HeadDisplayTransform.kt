package jp.awabi2048.cccontent.mob.ability

import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f

fun headDisplayTransformation(scale: Float): Transformation {
    return Transformation(
        Vector3f(0f, 0f, 0f),
        Quaternionf().rotateY(Math.PI.toFloat()),
        Vector3f(scale, scale, scale),
        Quaternionf()
    )
}
