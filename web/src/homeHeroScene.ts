import * as THREE from 'three';

const LIGHT_INTENSITY = 10;
const RAMP_DURATION = 7;

type FlowShader = { uniforms: { uTime: { value: number } } };

/**
 * Original glass-and-wall hero. The flowing field is not derived from another product's shaders.
 * Returns a dispose function. No-ops when WebGL cannot start (tests, old browsers).
 */
export function mountHomeHero(canvas: HTMLCanvasElement): () => void {
  if (typeof WebGLRenderingContext === 'undefined') return () => {};
  let renderer: THREE.WebGLRenderer;
  try {
    renderer = new THREE.WebGLRenderer({
      canvas,
      antialias: true,
      alpha: true,
      powerPreference: 'high-performance',
    });
  } catch {
    return () => {};
  }

  renderer.setClearColor(0x07090a, 1);
  renderer.toneMapping = THREE.NoToneMapping;
  renderer.outputColorSpace = THREE.SRGBColorSpace;

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(35, 1, 0.1, 100);
  camera.position.set(0, 0, 8);

  const group = new THREE.Group();
  scene.add(group);

  const wallMat = new THREE.MeshStandardMaterial({
    color: 0xffffff,
    roughness: 0.86,
    metalness: 0,
  });
  wallMat.customProgramCacheKey = () => 'wall-flow-field';
  wallMat.onBeforeCompile = (shader) => {
    shader.uniforms.uTime = { value: 0 };
    shader.vertexShader = shader.vertexShader
      .replace('#include <common>', '#include <common>\nvarying vec3 vWallPos;')
      .replace('#include <begin_vertex>', '#include <begin_vertex>\nvWallPos = position;');
    shader.fragmentShader = shader.fragmentShader
      .replace('#include <common>', '#include <common>\nuniform float uTime;\nvarying vec3 vWallPos;')
      .replace('#include <color_fragment>', `#include <color_fragment>
      float t = uTime;
      vec2 p = vWallPos.xy;
      // Original flowing interference — the band motion from the approved preview.
      // freq < 1 makes the color patches larger (0.7 ≈ 1.4× feature size).
      const float freq = 0.7;
      float d1 = sin(dot(p, vec2(0.72, 1.55)) * 2.35 * freq - t * 0.47);
      float d2 = sin(dot(p, vec2(0.38, 1.95)) * 1.15 * freq + t * 0.23);
      float d3 = sin(dot(p, vec2(-0.15, 1.10)) * 3.4 * freq - t * 0.31);
      vec2 c = p - vec2(sin(t * 0.11) * 0.85, cos(t * 0.09) * 0.55);
      float rad = sin(length(c) * 3.2 * freq - t * 0.38);
      float field = (d1 * 1.15 + d2 * 0.85 + d3 * 0.35 + rad * 0.55) / 2.9;
      float edge = smoothstep(0.15, 1.15, length(p * vec2(0.7, 0.9)));
      float width = mix(0.85, 0.22, edge);
      float sharp = mix(1.6, 4.4, edge);
      float graded = 0.5 + 0.5 * field;
      float mapped = smoothstep(0.5 - width, 0.5 + width, graded);
      mapped = pow(clamp(mapped, 0.0, 1.0), sharp);
      vec3 black = vec3(0.015, 0.008, 0.012);
      vec3 pink  = vec3(0.824, 0.086, 0.592); // #D21697, the band color
      diffuseColor.rgb = mix(black, pink, mapped);
    `);
    wallMat.userData.shader = shader;
  };

  const backdrop = new THREE.Mesh(new THREE.PlaneGeometry(22, 16), wallMat);
  backdrop.position.z = -1.6;
  group.add(backdrop);

  const sides: THREE.Mesh[] = [];
  for (const [x, rotY] of [[-11, Math.PI / 2], [11, -Math.PI / 2]] as const) {
    const side = new THREE.Mesh(new THREE.PlaneGeometry(8, 16), wallMat);
    side.position.set(x * 0.5, 0, -0.4);
    side.rotation.y = rotY;
    group.add(side);
    sides.push(side);
  }

  const pointLight = new THREE.PointLight(0xffffff, 0, 12, 2);
  pointLight.position.set(0, 0, -0.85);
  scene.add(pointLight);

  const glass = new THREE.MeshPhysicalMaterial({
    transmission: 1,
    thickness: 1.5,
    roughness: 0.14,
    ior: 1.4,
    color: 0xffffff,
    metalness: 0,
    attenuationColor: new THREE.Color(0xD21697),
    attenuationDistance: 4.5,
    transparent: true,
    side: THREE.DoubleSide,
  });

  const barGeo = hollowCylinderGeometry(0.92 / 2, 0.92 / 2 - 0.92 / 10, 16, 48);
  const bars: THREE.Mesh[] = [];
  for (let i = -8; i <= 8; i++) {
    const bar = new THREE.Mesh(barGeo, glass);
    bar.rotation.z = Math.PI / 4;
    bar.position.x = i * 0.78;
    bar.position.z = 0.7;
    const edge = Math.abs(i) / 8;
    const s = 1.18 - edge * 0.48;
    bar.scale.set(s, 1, s);
    group.add(bar);
    bars.push(bar);
  }

  const resize = () => {
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    const w = canvas.clientWidth;
    const h = canvas.clientHeight;
    renderer.setPixelRatio(dpr);
    renderer.setSize(w, h, false);
    camera.aspect = w / Math.max(h, 1);
    camera.updateProjectionMatrix();
  };
  const resizeObserver = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(resize);
  resizeObserver?.observe(canvas);
  resize();

  const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
  let playing = !reduced;
  const intersection = typeof IntersectionObserver === 'undefined'
    ? null
    : new IntersectionObserver((entries) => {
      playing = !reduced && entries[0]?.isIntersecting === true;
    }, { threshold: 0.05 });
  intersection?.observe(canvas);

  const t0 = performance.now();
  const flowShader = () => wallMat.userData.shader as FlowShader | undefined;
  renderer.setAnimationLoop((now) => {
    if (!playing && now !== t0) return;
    const t = (now - t0) / 1000;
    pointLight.intensity = Math.min(t / RAMP_DURATION, 1) * LIGHT_INTENSITY;
    const shader = flowShader();
    if (shader) shader.uniforms.uTime.value = t * 0.45;
    group.rotation.y = Math.sin(t * 0.18) * 0.22;
    group.rotation.x = Math.sin(t * 0.13) * 0.12;
    group.rotation.z = Math.sin(t * 0.07) * 0.06;
    renderer.render(scene, camera);
  });

  if (reduced) {
    renderer.setAnimationLoop(null);
    pointLight.intensity = LIGHT_INTENSITY;
    const shader = flowShader();
    if (shader) shader.uniforms.uTime.value = 0.8;
    renderer.render(scene, camera);
  }

  return () => {
    renderer.setAnimationLoop(null);
    resizeObserver?.disconnect();
    intersection?.disconnect();
    barGeo.dispose();
    glass.dispose();
    wallMat.dispose();
    backdrop.geometry.dispose();
    for (const side of sides) side.geometry.dispose();
    bars.length = 0;
    renderer.dispose();
  };
}

function hollowCylinderGeometry(outerR: number, innerR: number, height: number, radialSegs: number) {
  const shape = new THREE.Shape();
  shape.absarc(0, 0, outerR, 0, Math.PI * 2, false);
  const hole = new THREE.Path();
  hole.absarc(0, 0, innerR, 0, Math.PI * 2, true);
  shape.holes.push(hole);
  const geo = new THREE.ExtrudeGeometry(shape, {
    depth: height,
    bevelEnabled: false,
    curveSegments: radialSegs,
    steps: 1,
  });
  geo.translate(0, 0, -height / 2);
  geo.rotateX(-Math.PI / 2);
  geo.computeVertexNormals();
  return geo;
}
