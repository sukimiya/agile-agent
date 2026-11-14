import * as THREE from 'three';

/**
 * /home hero. A box with an animated colour field sits behind a row of glass cylinders. The cylinders
 * do not shade themselves: every pixel refracts a view ray through the glass and reads the box from an
 * offscreen buffer, several times with a rough, per-pixel random normal. That random sampling is what
 * gives the grainy edges, the per-channel index of refraction gives the blue fringes, and a slow noise
 * field added to the normal makes the slats warp over time.
 */

const BG = new THREE.Color().setRGB(7 / 255, 8 / 255, 10 / 255);
/** Colour ramp of the box, dark to bright. The shader cubes it, so mid tones land on brand pink #D21697. */
const RAMP = [
  new THREE.Color().setRGB(184 / 255, 2 / 255, 132 / 255),
  new THREE.Color().setRGB(246 / 255, 118 / 255, 214 / 255),
  new THREE.Color().setRGB(255 / 255, 246 / 255, 252 / 255),
];

const CAMERA_FOV = 35;
const CAMERA_Z = 16.54;
const BOX_SIZE = 7;
const BOX_Z = -9;
const BOX_SPEED = 0.1;
const ROD_COUNT = 16;
const ROD_RADIUS = 0.5;
const ROD_HEIGHT = 15;
const ROD_ROTATION = 0.73;
const BUFFER_SIZE = 1024;
const SAMPLES = 6;
const SAMPLES_NARROW = 5;
/** Visible world width below which the scene counts as narrow: fewer samples and no warp. */
const NARROW_WIDTH = 7.3;
const WARP_MAX = 15;
const WARP_PER_SECOND = 0.36;
const WARP_DRIFT = 0.025;
const POINTER_TURN = 0.3;
const STOP_AFTER_SECONDS = 200;
const STILL_TIME = 6;

const boxShader = {
  vertexShader: /* glsl */ `
    varying vec4 vClip;
    void main() {
      vClip = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
      gl_Position = vClip;
    }
  `,
  fragmentShader: /* glsl */ `
    uniform float uTime;
    uniform vec3 uDark;
    uniform vec3 uMid;
    uniform vec3 uBright;
    varying vec4 vClip;
    void main() {
      vec2 p = (vClip.xy / vClip.w * 0.5 + 0.5) * vec2(0.85, 1.3);
      float t = uTime;
      float sweep = cos(3.0 * p.x + t);
      float turn = cos(p.x * cos(t) + 5.0 * p.y * sin(t) + t);
      vec2 c = 0.3 * p - 0.5 + vec2(cos(t), sin(t * 0.5));
      float ring = sin(20.0 * sqrt(dot(c, c) + 1.0) + t);
      float v = (sweep + turn + ring) / 6.0 + 0.5;
      vec3 col = v < 0.5 ? mix(uDark, uMid, v * 2.0) : mix(uMid, uBright, v * 2.0 - 1.0);
      gl_FragColor = vec4(col * col * col, 1.0);
    }
  `,
};

const glassShader = {
  vertexShader: /* glsl */ `
    varying vec3 vWorld;
    varying vec3 vNormal;
    void main() {
      vec4 local = vec4(position, 1.0);
      vec3 n = normal;
      #ifdef USE_INSTANCING
        local = instanceMatrix * local;
        n = mat3(instanceMatrix) * n;
      #endif
      vec4 world = modelMatrix * local;
      vWorld = world.xyz;
      vNormal = mat3(modelMatrix) * n;
      gl_Position = projectionMatrix * viewMatrix * world;
    }
  `,
  fragmentShader: /* glsl */ `
    uniform sampler2D uBuffer;
    uniform mat4 uProjection;
    uniform float uTime;
    uniform float uThickness;
    uniform float uRoughness;
    uniform float uBlur;
    uniform float uIor;
    uniform float uAberration;
    uniform float uWarp;
    uniform float uWarpScale;
    uniform float uWarpDrift;
    varying vec3 vWorld;
    varying vec3 vNormal;

    uint pcg(uint v) {
      uint s = v * 747796405u + 2891336453u;
      uint w = ((s >> ((s >> 28u) + 4u)) ^ s) * 277803737u;
      return (w >> 22u) ^ w;
    }
    float unit(uint h) { return float(h) / 4294967295.0; }
    float rand(float seed) {
      uvec2 p = uvec2(gl_FragCoord.xy);
      return unit(pcg(p.x + pcg(p.y + pcg(uint(seed)))));
    }

    vec3 gradient(vec3 cell) {
      ivec3 c = ivec3(cell);
      uint h = pcg(uint(c.x) + pcg(uint(c.y) + pcg(uint(c.z))));
      vec3 g = vec3(float(h & 1023u), float((h >> 10u) & 1023u), float((h >> 20u) & 1023u)) / 511.5 - 1.0;
      return normalize(g + 1e-4);
    }
    float noise(vec3 p) {
      vec3 i = floor(p);
      vec3 f = p - i;
      vec3 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
      vec2 o = vec2(0.0, 1.0);
      float x00 = mix(dot(gradient(i + o.xxx), f - o.xxx), dot(gradient(i + o.yxx), f - o.yxx), u.x);
      float x10 = mix(dot(gradient(i + o.xyx), f - o.xyx), dot(gradient(i + o.yyx), f - o.yyx), u.x);
      float x01 = mix(dot(gradient(i + o.xxy), f - o.xxy), dot(gradient(i + o.yxy), f - o.yxy), u.x);
      float x11 = mix(dot(gradient(i + o.xyy), f - o.xyy), dot(gradient(i + o.yyy), f - o.yyy), u.x);
      return 1.5 * mix(mix(x00, x10, u.y), mix(x01, x11, u.y), u.z);
    }
    float fbm(vec3 p) {
      return 0.5333 * noise(p) + 0.2667 * noise(2.0 * p) + 0.1333 * noise(4.0 * p) + 0.0667 * noise(8.0 * p);
    }

    /** One channel of the buffer where a ray refracted at this pixel leaves the glass. */
    vec3 refracted(vec3 n, vec3 view, float ior, float depth) {
      vec3 exit = vWorld + normalize(refract(-view, n, 1.0 / ior)) * depth;
      vec4 clip = uProjection * viewMatrix * vec4(exit, 1.0);
      return texture2D(uBuffer, clip.xy / clip.w * 0.5 + 0.5).rgb;
    }

    void main() {
      vec3 view = normalize(cameraPosition - vWorld);
      vec3 n = normalize(vNormal);
      float seed = 0.0;
      float jitter = rand(seed++);
      float smear = uThickness * max(pow(uRoughness, 0.33), uBlur);

      vec3 warp = vec3(0.0);
      if (uWarp > 0.0) {
        vec3 drift = vec3(uTime, -uTime, -uTime) * uWarpDrift;
        warp = uWarp * vec3(
          fbm(vWorld * uWarpScale + drift),
          fbm(vWorld.zxy * uWarpScale - drift),
          fbm(vWorld.yxz * uWarpScale + drift)
        );
      }

      vec3 sum = vec3(0.0);
      for (float i = 0.0; i < float(SAMPLES); i++) {
        vec3 dir = normalize(vec3(rand(seed++), rand(seed++), rand(seed++)) - 0.5);
        vec3 sn = normalize(n + uRoughness * uRoughness * 2.0 * dir * pow(rand(seed++), 0.33) + warp);
        float progress = (i + jitter) / float(SAMPLES);
        float depth = uThickness + smear * progress;
        float spread = uAberration * progress;
        sum.r += refracted(sn, view, uIor, depth).r;
        sum.g += refracted(sn, view, uIor * (1.0 + spread), depth).g;
        sum.b += refracted(sn, view, uIor * (1.0 + 2.0 * spread), depth).b;
      }
      gl_FragColor = vec4(sum / float(SAMPLES), 1.0);
    }
  `,
};

/** Critically damped spring towards `target`; `state.v` carries the velocity between calls. */
function smoothDamp(current: number, target: number, state: { v: number }, smoothTime: number, dt: number) {
  const omega = 2 / smoothTime;
  const x = omega * dt;
  const decay = 1 / (1 + x + 0.48 * x * x + 0.235 * x * x * x);
  const change = current - target;
  const temp = (state.v + omega * change) * dt;
  state.v = (state.v - omega * temp) * decay;
  return target + (change + temp) * decay;
}

/**
 * Mounts the hero on `canvas` and returns a dispose function.
 * No-ops when WebGL2 cannot start (tests, old browsers).
 */
export function mountHomeHero(canvas: HTMLCanvasElement): () => void {
  if (typeof WebGL2RenderingContext === 'undefined') return () => {};
  let renderer: THREE.WebGLRenderer;
  try {
    renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: 'high-performance' });
  } catch {
    return () => {};
  }
  // Colours are authored as display values, so skip tone mapping and the sRGB transfer.
  renderer.toneMapping = THREE.NoToneMapping;
  renderer.outputColorSpace = THREE.LinearSRGBColorSpace;

  const scene = new THREE.Scene();
  scene.background = BG;
  const camera = new THREE.PerspectiveCamera(CAMERA_FOV, 1, 0.01, 100);
  camera.position.set(0, 0, CAMERA_Z);

  const boxMat = new THREE.ShaderMaterial({
    uniforms: {
      uTime: { value: 0 },
      uDark: { value: RAMP[0] },
      uMid: { value: RAMP[1] },
      uBright: { value: RAMP[2] },
    },
    vertexShader: boxShader.vertexShader,
    fragmentShader: boxShader.fragmentShader,
  });
  const boxGeo = new THREE.BoxGeometry(BOX_SIZE, BOX_SIZE, BOX_SIZE);
  const box = new THREE.Mesh(boxGeo, boxMat);
  box.position.z = BOX_Z;
  box.scale.setScalar(0);
  scene.add(box);

  const buffer = new THREE.WebGLRenderTarget(BUFFER_SIZE, BUFFER_SIZE, { type: THREE.HalfFloatType });
  const glassMat = new THREE.ShaderMaterial({
    defines: { SAMPLES },
    uniforms: {
      uBuffer: { value: buffer.texture },
      uProjection: { value: camera.projectionMatrix },
      uTime: { value: 0 },
      uThickness: { value: 1 },
      uRoughness: { value: 0.35 },
      uBlur: { value: 2.88 },
      uIor: { value: 1.5 },
      uAberration: { value: 3 },
      uWarp: { value: 0 },
      uWarpScale: { value: 0.08 },
      uWarpDrift: { value: 0 },
    },
    vertexShader: glassShader.vertexShader,
    fragmentShader: glassShader.fragmentShader,
  });
  const rodGeo = new THREE.CylinderGeometry(ROD_RADIUS, ROD_RADIUS, ROD_HEIGHT, 8, 1);
  const rods = new THREE.InstancedMesh(rodGeo, glassMat, ROD_COUNT);
  const place = new THREE.Matrix4();
  for (let i = 0; i < ROD_COUNT; i++) {
    rods.setMatrixAt(i, place.makeTranslation((i + 1) * 2 * ROD_RADIUS - ROD_COUNT * ROD_RADIUS, 0, 0));
  }
  rods.frustumCulled = false;
  rods.rotation.z = ROD_ROTATION;
  scene.add(rods);

  const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
  let narrow = false;

  const draw = () => {
    rods.visible = false;
    renderer.setRenderTarget(buffer);
    renderer.render(scene, camera);
    rods.visible = true;
    renderer.setRenderTarget(null);
    renderer.render(scene, camera);
  };

  /** Still frame for reduced motion: settled box, moderate warp, nothing animating. */
  const drawStill = () => {
    box.scale.setScalar(1);
    boxMat.uniforms.uTime.value = STILL_TIME * BOX_SPEED;
    glassMat.uniforms.uTime.value = STILL_TIME;
    glassMat.uniforms.uWarp.value = narrow ? 0 : WARP_PER_SECOND * STILL_TIME;
    glassMat.uniforms.uWarpDrift.value = WARP_DRIFT;
    draw();
  };

  const resize = () => {
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    const w = Math.max(canvas.clientWidth, 1);
    const h = Math.max(canvas.clientHeight, 1);
    renderer.setPixelRatio(dpr);
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
    const worldWidth = 2 * Math.tan(THREE.MathUtils.degToRad(CAMERA_FOV / 2)) * CAMERA_Z * camera.aspect;
    const nowNarrow = worldWidth <= NARROW_WIDTH;
    if (nowNarrow !== narrow) {
      narrow = nowNarrow;
      glassMat.defines.SAMPLES = narrow ? SAMPLES_NARROW : SAMPLES;
      glassMat.needsUpdate = true;
    }
    if (reduced) drawStill();
  };
  const resizeObserver = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(resize);
  resizeObserver?.observe(canvas);
  resize();

  const pointer = { x: 0, y: 0 };
  const onPointer = (e: PointerEvent) => {
    pointer.x = (e.clientX / window.innerWidth) * 2 - 1;
    pointer.y = -(e.clientY / window.innerHeight) * 2 + 1;
  };
  window.addEventListener('pointermove', onPointer, { passive: true });

  let visible = true;
  const intersection = typeof IntersectionObserver === 'undefined'
    ? null
    : new IntersectionObserver((entries) => {
      visible = entries[0]?.isIntersecting === true;
    }, { threshold: 0 });
  intersection?.observe(canvas);

  const scaleVel = { v: 0 };
  const turnXVel = { v: 0 };
  const turnYVel = { v: 0 };
  let t = 0;
  let last = performance.now();
  if (!reduced) {
    renderer.setAnimationLoop((now) => {
      const dt = Math.min((now - last) / 1000, 0.1);
      last = now;
      if (!visible) return;
      t += dt;
      if (t > STOP_AFTER_SECONDS) {
        renderer.setAnimationLoop(null);
        return;
      }

      boxMat.uniforms.uTime.value = t * BOX_SPEED;
      box.scale.setScalar(smoothDamp(box.scale.x, 1, scaleVel, 1, dt));
      box.rotation.x = smoothDamp(box.rotation.x, pointer.x * POINTER_TURN, turnXVel, 0.5, dt);
      box.rotation.y = smoothDamp(box.rotation.y, -pointer.y * POINTER_TURN, turnYVel, 0.5, dt);

      glassMat.uniforms.uTime.value = t;
      if (!narrow) {
        glassMat.uniforms.uWarp.value = Math.min(WARP_MAX, t * WARP_PER_SECOND);
        glassMat.uniforms.uWarpDrift.value = WARP_DRIFT;
      } else {
        glassMat.uniforms.uWarp.value = 0;
      }
      draw();
    });
  }

  return () => {
    renderer.setAnimationLoop(null);
    window.removeEventListener('pointermove', onPointer);
    resizeObserver?.disconnect();
    intersection?.disconnect();
    boxGeo.dispose();
    boxMat.dispose();
    rodGeo.dispose();
    glassMat.dispose();
    rods.dispose();
    buffer.dispose();
    renderer.dispose();
  };
}
