package com.example.ui.character

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

data class GlbAnimationTrack(
    val name: String,
    val times: FloatArray,
    val rotations: Array<FloatArray> // [x, y, z, w] quaternion per keyframe
)

data class GlbLoadedModel(
    val assetPath: String,
    val vertexCount: Int,
    val triangleCount: Int,
    val basePositions: FloatArray, // length = vertexCount * 3
    val baseNormals: FloatArray,   // length = vertexCount * 3
    val vertexColors: FloatArray,  // length = vertexCount * 4
    val indices: IntArray,         // length = triangleCount * 3
    val morphTargetNames: List<String>,
    val morphDeltas: Map<String, FloatArray>, // targetName -> float[vertexCount * 3]
    val animationNames: List<String>,
    val animations: Map<String, GlbAnimationTrack>
)

/**
 * Binary glTF 2.0 (.glb) parser that reads real .glb files from app/src/main/assets/characters/,
 * verifies magic header 0x46546C67 ("glTF"), extracts mesh primitives, morph targets, and animation
 * tracks, and logs all discovered animation clips and morph targets to Logcat.
 */
object GlbLoader {

    private const val TAG = "GlbCharacterStage"
    private const val GLTF_MAGIC = 0x46546C67
    private const val CHUNK_JSON = 0x4E4F534A
    private const val CHUNK_BIN = 0x004E4942

    private val cache = ConcurrentHashMap<String, GlbLoadedModel>()

    fun loadGlbAsset(context: Context, assetPath: String): GlbLoadedModel? {
        cache[assetPath]?.let { return it }

        return try {
            val bytes = context.assets.open(assetPath).use { it.readBytes() }
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            val magic = buf.int
            val version = buf.int
            val totalLength = buf.int
            if (magic != GLTF_MAGIC || version != 2 || totalLength <= 20) {
                Log.e(TAG, "Invalid .glb header in $assetPath (magic=0x${magic.toString(16)}, version=$version)")
                return null
            }

            val jsonChunkLen = buf.int
            val jsonChunkType = buf.int
            if (jsonChunkType != CHUNK_JSON) {
                Log.e(TAG, "Missing JSON chunk in $assetPath")
                return null
            }
            val jsonBytes = ByteArray(jsonChunkLen)
            buf.get(jsonBytes)
            val jsonRoot = JSONObject(String(jsonBytes, Charsets.UTF_8).trim())

            val binChunkLen = buf.int
            val binChunkType = buf.int
            if (binChunkType != CHUNK_BIN) {
                Log.e(TAG, "Missing BIN chunk in $assetPath")
                return null
            }
            val binStartOffset = buf.position()
            val binBuffer = ByteBuffer.wrap(bytes, binStartOffset, binChunkLen).slice().order(ByteOrder.LITTLE_ENDIAN)

            val bufferViews = jsonRoot.getJSONArray("bufferViews")
            val accessors = jsonRoot.getJSONArray("accessors")

            fun readFloatAccessor(accessorIdx: Int, componentsPerElement: Int): FloatArray {
                val acc = accessors.getJSONObject(accessorIdx)
                val bvIdx = acc.getInt("bufferView")
                val count = acc.getInt("count")
                val bv = bufferViews.getJSONObject(bvIdx)
                val byteOffset = bv.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0)
                val out = FloatArray(count * componentsPerElement)
                val view = binBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                view.position(byteOffset)
                view.asFloatBuffer().get(out)
                return out
            }

            fun readUShortAccessor(accessorIdx: Int): IntArray {
                val acc = accessors.getJSONObject(accessorIdx)
                val bvIdx = acc.getInt("bufferView")
                val count = acc.getInt("count")
                val bv = bufferViews.getJSONObject(bvIdx)
                val byteOffset = bv.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0)
                val out = IntArray(count)
                val view = binBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                view.position(byteOffset)
                for (i in 0 until count) {
                    out[i] = view.short.toInt() and 0xFFFF
                }
                return out
            }

            val meshObj = jsonRoot.getJSONArray("meshes").getJSONObject(0)
            val primObj = meshObj.getJSONArray("primitives").getJSONObject(0)
            val attrs = primObj.getJSONObject("attributes")

            val posAcc = attrs.getInt("POSITION")
            val normAcc = attrs.getInt("NORMAL")
            val colAcc = attrs.getInt("COLOR_0")
            val idxAcc = primObj.getInt("indices")

            val basePositions = readFloatAccessor(posAcc, 3)
            val baseNormals = readFloatAccessor(normAcc, 3)
            val vertexColors = readFloatAccessor(colAcc, 4)
            val indices = readUShortAccessor(idxAcc)
            val vertexCount = basePositions.size / 3

            val morphNames = mutableListOf<String>()
            val extras = meshObj.optJSONObject("extras")
            val targetNamesArr = extras?.optJSONArray("targetNames")
            if (targetNamesArr != null) {
                for (i in 0 until targetNamesArr.length()) {
                    morphNames.add(targetNamesArr.getString(i))
                }
            }

            val morphDeltas = linkedMapOf<String, FloatArray>()
            val targetsArr = primObj.optJSONArray("targets")
            if (targetsArr != null) {
                for (i in 0 until targetsArr.length()) {
                    val tObj = targetsArr.getJSONObject(i)
                    val targetName = morphNames.getOrElse(i) { "target_$i" }
                    if (tObj.has("POSITION")) {
                        val deltaAcc = tObj.getInt("POSITION")
                        morphDeltas[targetName] = readFloatAccessor(deltaAcc, 3)
                    }
                }
            }

            val animNames = mutableListOf<String>()
            val animations = linkedMapOf<String, GlbAnimationTrack>()
            val animsArr = jsonRoot.optJSONArray("animations")
            if (animsArr != null) {
                for (i in 0 until animsArr.length()) {
                    val aObj = animsArr.getJSONObject(i)
                    val name = aObj.optString("name", "anim_$i")
                    animNames.add(name)
                    val sampler = aObj.getJSONArray("samplers").getJSONObject(0)
                    val times = readFloatAccessor(sampler.getInt("input"), 1)
                    val rawQuats = readFloatAccessor(sampler.getInt("output"), 4)
                    val quats = Array(times.size) { k ->
                        floatArrayOf(
                            rawQuats[k * 4],
                            rawQuats[k * 4 + 1],
                            rawQuats[k * 4 + 2],
                            rawQuats[k * 4 + 3]
                        )
                    }
                    animations[name] = GlbAnimationTrack(name, times, quats)
                }
            }

            val loaded = GlbLoadedModel(
                assetPath = assetPath,
                vertexCount = vertexCount,
                triangleCount = indices.size / 3,
                basePositions = basePositions,
                baseNormals = baseNormals,
                vertexColors = vertexColors,
                indices = indices,
                morphTargetNames = morphNames,
                morphDeltas = morphDeltas,
                animationNames = animNames,
                animations = animations
            )

            Log.i(
                TAG,
                "Verified .glb asset [$assetPath]: vertices=$vertexCount, triangles=${loaded.triangleCount}, " +
                    "animations=$animNames, morphTargets=$morphNames"
            )
            cache[assetPath] = loaded
            loaded
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load .glb asset [$assetPath]: ${e.message}", e)
            null
        }
    }
}
