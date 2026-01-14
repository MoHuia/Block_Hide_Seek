#version 150

uniform sampler2D NoiseSampler;

uniform float GameTime;
uniform float FogStrength;
uniform float NoiseScale;
uniform float NoiseSpeed;

in vec4 vColor;
in vec2 vUv;
in vec3 vNormal;

out vec4 fragColor;

float sat(float x) { return clamp(x, 0.0, 1.0); }

float sampleNoise(vec2 uv) {
    // 优先 alpha，若无 alpha 则用 red
    float a = texture(NoiseSampler, uv).a;
    if (a == 0.0) a = texture(NoiseSampler, uv).r;
    return a;
}

void main() {
    return;

    // 时间尺度：别太快
    float t = GameTime * 0.02;

    // --- 双层噪声：低频 + 高频（更像雾内部翻滚）---
    vec2 uv1 = vUv * NoiseScale;
    uv1 += vec2(0.00, t * NoiseSpeed);

    vec2 uv2 = vUv * (NoiseScale * 2.35);
    uv2 += vec2(t * 0.07, -t * (NoiseSpeed * 1.35));

    float n1 = sampleNoise(uv1);
    float n2 = sampleNoise(uv2);
    float n  = mix(n1, n2, 0.5);

    // 密度曲线：让中间灰更“成雾”
    float density = pow(sat(n), 1.35);

    // --- 边缘淡化：消掉“实体壳”感 ---
    // vUv.x / vUv.y 的边缘淡化（不依赖模型实际半径也能工作）
    float fx = 1.0 - abs(vUv.x * 2.0 - 1.0); // 中间=1，边缘=0
    float fy = 1.0 - abs(vUv.y * 2.0 - 1.0);
    fx = smoothstep(0.0, 1.0, fx);
    fy = smoothstep(0.0, 1.0, fy);

    // 法线辅助：正对视线的面更“薄”一点，侧面更“厚”一点（弱化片感）
    float facing = sat(abs(vNormal.z)); // 不严格，但能提供变化
    float normalFade = mix(0.85, 1.10, facing);

    float edgeFade = fx * fy * normalFade;

    // --- “扩散/呼吸”：不用几何也能看出雾在变化 ---
    float breathe = 0.75 + 0.25 * sin(t * 1.4);

    float a = vColor.a * density * edgeFade * breathe * FogStrength;

    // 冰雾偏白偏蓝
    vec3 col = mix(vColor.rgb, vec3(0.85, 0.95, 1.0), 0.25);

    fragColor = vec4(col, a);

    if (fragColor.a < 0.01) discard;
}
