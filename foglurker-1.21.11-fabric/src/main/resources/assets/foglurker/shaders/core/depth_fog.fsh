#version 150

// Fog Lurker's own fog: drawn over the finished world when another mod has switched Minecraft's fog off.
// How far each pixel is comes from a copy of the depth buffer, so it is real distance fog, not a flat tint.
uniform sampler2D DepthSampler;
uniform vec3 VeilColor;  // the fog colour
uniform vec2 ProjScale;  // tan(half the field of view), sideways and up
uniform vec2 Planes;     // the camera's near and far planes
uniform vec2 FogRange;   // where the fog starts and where nothing can be seen any more (blocks)
uniform vec2 Alphas;     // how thick it gets (on the world; the sky follows the same distances)
uniform mat4 InvViewRot; // camera space to world directions (to know how high above the horizon a sky pixel is)

in vec2 texCoord;

out vec4 fragColor;

void main() {
    float depth = texture(DepthSampler, texCoord).r;
    vec2 ndc = texCoord * 2.0 - 1.0;
    float dist;
    if (depth >= 1.0) {
        // Nothing drawn here: the sky. Minecraft fogs its sky dome (16 blocks overhead) by distance too, so it
        // stays clear straight up and turns to fog toward the horizon.
        vec3 dir = normalize((InvViewRot * vec4(normalize(vec3(ndc * ProjScale, -1.0)), 0.0)).xyz);
        dist = dir.y > 0.001 ? 16.0 / dir.y : 1.0e6;
    } else {
        float n = Planes.x;
        float f = Planes.y;
        float z = depth * 2.0 - 1.0;
        float viewZ = (2.0 * n * f) / (f + n - z * (f - n));
        dist = viewZ * length(vec3(ndc * ProjScale, 1.0)); // along the ray, like Minecraft's own fog
    }
    float a = clamp((dist - FogRange.x) / max(FogRange.y - FogRange.x, 0.001), 0.0, 1.0) * Alphas.x;
    fragColor = vec4(VeilColor, a);
}
