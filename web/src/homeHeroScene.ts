import * as THREE from 'three';
import { RoundedBoxGeometry } from 'three/examples/jsm/geometries/RoundedBoxGeometry.js';
import { EffectComposer } from 'three/examples/jsm/postprocessing/EffectComposer.js';
import { RenderPass } from 'three/examples/jsm/postprocessing/RenderPass.js';
import { UnrealBloomPass } from 'three/examples/jsm/postprocessing/UnrealBloomPass.js';
import { OutputPass } from 'three/examples/jsm/postprocessing/OutputPass.js';
import { ShaderPass } from 'three/examples/jsm/postprocessing/ShaderPass.js';

const BG = 0x07080a;
const PINK = new THREE.Color(0xd21697);
const SLAT_COUNT = 11;
const SLAT_GAP = 1.95;
const INTRO_SECONDS = 2.6;
const REST_TILT = -0.48;
const OPEN_TILT = -0.18;

/** Glowing light field behind the slats: a soft pink hotspot that drifts and follows the pointer. */
const backdropShader = {
  uniforms: {
    uTime: { value: 0 },
    uLight: { value: new THREE.Vector2(0, 0) },
    uPower: { value: 0 },
    uColor: { value: PINK.clone() },
  },
  vertexShader: /* glsl */ `
    varying vec2 vPos;
    void main() {
      vPos = position.xy;
      gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
    }
  `,
  fragmentShader: /* glsl */ `
    uniform float uTime;
    uniform vec2 uLight;
    uniform float uPower;
    uniform vec3 uColor;
    varying vec2 vPos;
    void main() {
      vec2 p = vPos - uLight;
      float flow = sin(dot(vPos, vec2(0.55, -0.55)) * 0.9 - uTime * 0.6) * 0.5 + 0.5;
      float glow = exp(-dot(p * vec2(0.62, 0.75), p * vec2(0.62, 0.75)));
      float core = exp(-dot(p, p) * 0.9);
      vec3 col = uColor * (glow * (0.5 + 0.5 * flow) + core * 0.35) * uPower;
      gl_FragColor = vec4(col, 1.0);
    }
  `,
};

/** Film grain plus a stochastic dissolve where the light falls off, then a radial fade into the page. */
const grainShader = {
  uniforms: {
    tDiffuse: { value: null as THREE.Texture | null },
    uTime: { value: 0 },
    uResolution: { value: new THREE.Vector2(1, 1) },
    uBg: { value: new THREE.Color(BG) },
  },
  vertexShader: /* glsl */ `
    varying vec2 vUv;
    void main() {
      vUv = uv;
      gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
    }
  `,
  fragmentShader: /* glsl */ `
    uniform sampler2D tDiffuse;
    uniform float uTime;
    uniform vec2 uResolution;
    uniform vec3 uBg;
    varying vec2 vUv;
    float hash(vec2 p) {
      p = fract(p * vec2(123.34, 456.21));
      p += dot(p, p + 45.32);
      return fract(p.x * p.y);
    }
    void main() {
      vec3 col = texture2D(tDiffuse, vUv).rgb;
      float n = hash(floor(vUv * uResolution) + floor(uTime * 24.0));
      float lum = dot(col, vec3(0.299, 0.587, 0.114));
      float keep = smoothstep(n * 0.2, n * 0.2 + 0.05, lum);
      col = mix(uBg, col, keep);
      col += (n - 0.5) * 0.03;
      vec2 c = (vUv - 0.5) * vec2(uResolution.x / uResolution.y, 1.0);
      float fade = smoothstep(0.7, 0.18, length(c * vec2(0.9, 1.0)));
      gl_FragColor = vec4(mix(uBg, col, fade), 1.0);
    }
  `,
};

/**
 * Hero: tilted glass slats lit from behind, with grain and pointer/scroll parallax.
 * Returns a dispose function. No-ops when WebGL cannot start (tests, old browsers).
 */
export function mountHomeHero(canvas: HTMLCanvasElement): () => void {
  if (typeof WebGLRenderingContext === 'undefined') return () => {};
  let renderer: THREE.WebGLRenderer;
  try {
    renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: 'high-performance' });
  } catch {
    return () => {};
  }

  renderer.setClearColor(BG, 1);
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.05;
  renderer.outputColorSpace = THREE.SRGBColorSpace;

  const scene = new THREE.Scene();
  scene.background = new THREE.Color(BG);
  const camera = new THREE.PerspectiveCamera(30, 1, 0.1, 100);
  camera.position.set(0, 0, 14);

  const backdropMat = new THREE.ShaderMaterial({
    uniforms: THREE.UniformsUtils.clone(backdropShader.uniforms),
    vertexShader: backdropShader.vertexShader,
    fragmentShader: backdropShader.fragmentShader,
  });
  const backdrop = new THREE.Mesh(new THREE.PlaneGeometry(40, 28), backdropMat);
  backdrop.position.z = -3.2;
  scene.add(backdrop);

  const ambient = new THREE.AmbientLight(0xffffff, 0.02);
  scene.add(ambient);
  const key = new THREE.PointLight(PINK, 0, 11, 2);
  key.position.set(0, 0, 4);
  scene.add(key);
  const rim = new THREE.DirectionalLight(0xffd6ef, 0);
  rim.position.set(-6, 8, 6);
  scene.add(rim);

  const glass = new THREE.MeshPhysicalMaterial({
    color: 0xe0329f,
    roughness: 0.5,
    metalness: 0,
    transmission: 0.45,
    thickness: 0.9,
    ior: 1.45,
    clearcoat: 0.2,
    clearcoatRoughness: 0.4,
    attenuationColor: PINK,
    attenuationDistance: 1.1,
    specularIntensity: 1,
  });

  const slatGeo = new RoundedBoxGeometry(1.5, 22, 0.34, 4, 0.1);
  const rack = new THREE.Group();
  rack.rotation.z = Math.PI / 4;
  scene.add(rack);
  const slats: THREE.Mesh[] = [];
  const half = (SLAT_COUNT - 1) / 2;
  for (let i = 0; i < SLAT_COUNT; i++) {
    const slat = new THREE.Mesh(slatGeo, glass);
    slat.position.x = (i - half) * SLAT_GAP;
    slat.position.z = -Math.abs(i - half) * 0.08;
    rack.add(slat);
    slats.push(slat);
  }

  const composer = new EffectComposer(renderer);
  composer.addPass(new RenderPass(scene, camera));
  const bloom = new UnrealBloomPass(new THREE.Vector2(1, 1), 0.3, 0.6, 0.45);
  composer.addPass(bloom);
  composer.addPass(new OutputPass());
  const grain = new ShaderPass(grainShader);
  composer.addPass(grain);

  let redrawOnResize = false;
  const resize = () => {
    const dpr = Math.min(1.75, window.devicePixelRatio || 1);
    const w = Math.max(canvas.clientWidth, 1);
    const h = Math.max(canvas.clientHeight, 1);
    renderer.setPixelRatio(dpr);
    renderer.setSize(w, h, false);
    composer.setPixelRatio(dpr);
    composer.setSize(w, h);
    grain.uniforms.uResolution.value.set(w * dpr, h * dpr);
    camera.aspect = w / h;
    camera.fov = w / h < 1 ? 42 : 30;
    camera.updateProjectionMatrix();
    if (redrawOnResize) frame(INTRO_SECONDS, 1);
  };
  const resizeObserver = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(resize);
  resizeObserver?.observe(canvas);
  resize();

  const pointer = new THREE.Vector2();
  const eased = new THREE.Vector2();
  const onPointer = (e: PointerEvent) => {
    const r = canvas.getBoundingClientRect();
    pointer.set(((e.clientX - r.left) / r.width) * 2 - 1, -(((e.clientY - r.top) / r.height) * 2 - 1));
    pointer.clampScalar(-1.2, 1.2);
  };
  window.addEventListener('pointermove', onPointer, { passive: true });

  let scroll = 0;
  let easedScroll = 0;
  const onScroll = () => {
    const r = canvas.getBoundingClientRect();
    scroll = THREE.MathUtils.clamp(-r.top / Math.max(r.height, 1), 0, 1);
  };
  window.addEventListener('scroll', onScroll, { passive: true });
  onScroll();

  const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
  let visible = true;
  const intersection = typeof IntersectionObserver === 'undefined'
    ? null
    : new IntersectionObserver((entries) => {
      visible = entries[0]?.isIntersecting === true;
    }, { threshold: 0 });
  intersection?.observe(canvas);

  const frame = (t: number, dt: number) => {
    const intro = reduced ? 1 : easeOutCubic(Math.min(t / INTRO_SECONDS, 1));
    const k = 1 - Math.exp(-dt * 4);
    eased.lerp(pointer, k);
    easedScroll += (scroll - easedScroll) * k;

    const tilt = THREE.MathUtils.lerp(1.25, REST_TILT, intro)
      + (OPEN_TILT - REST_TILT) * easedScroll;
    slats.forEach((slat, i) => {
      const wave = reduced ? 0 : Math.sin(t * 0.7 - i * 0.45) * 0.05;
      slat.rotation.y = tilt + wave + eased.x * 0.12;
    });

    rack.rotation.x = -eased.y * 0.12 + Math.sin(t * 0.21) * 0.03;
    rack.rotation.y = eased.x * 0.16 + Math.sin(t * 0.17) * 0.04;
    rack.position.y = easedScroll * 3.2;
    rack.position.z = easedScroll * 1.5 - (1 - intro) * 2;

    const lx = eased.x * 3.2 + Math.sin(t * 0.23) * 1.2;
    const ly = eased.y * 2.2 + Math.cos(t * 0.19) * 0.8 + easedScroll * 2.5;
    backdropMat.uniforms.uLight.value.set(lx, ly);
    backdropMat.uniforms.uTime.value = t;
    backdropMat.uniforms.uPower.value = intro * (1.1 + easedScroll * 0.9);
    backdropMat.uniforms.uColor.value.copy(PINK).lerp(new THREE.Color(0xff7fc8), easedScroll * 0.6);
    key.position.set(lx * 0.8, ly * 0.8, 3.2);
    key.intensity = intro * (90 + easedScroll * 60);
    rim.intensity = intro * 0.5;

    grain.uniforms.uTime.value = t;
    composer.render(dt);
  };

  let t = 0;
  let last = performance.now();
  if (reduced) {
    redrawOnResize = true;
    frame(INTRO_SECONDS, 1);
  } else {
    renderer.setAnimationLoop((now) => {
      const dt = Math.min((now - last) / 1000, 0.1);
      last = now;
      if (!visible) return;
      t += dt;
      frame(t, dt);
    });
  }

  return () => {
    renderer.setAnimationLoop(null);
    window.removeEventListener('pointermove', onPointer);
    window.removeEventListener('scroll', onScroll);
    resizeObserver?.disconnect();
    intersection?.disconnect();
    slatGeo.dispose();
    glass.dispose();
    backdrop.geometry.dispose();
    backdropMat.dispose();
    bloom.dispose();
    composer.dispose();
    renderer.dispose();
  };
}

function easeOutCubic(x: number) {
  return 1 - (1 - x) ** 3;
}
