import fs from "node:fs/promises";
import path from "node:path";
import { pathToFileURL } from "node:url";
import { FileBlob, Presentation, PresentationFile } from "@oai/artifact-tool";

const workspaceDir = "/home/zxero/AndroidStudioProjects/locquiz";
const buildDir = path.join(workspaceDir, ".codex-ppt-build");
const renderDir = path.join(buildDir, "render-short-final");
const outputDir = path.join(workspaceDir, ".codex-ppt-output");
const finalPath = path.join(outputDir, "Bukal_Problem_Proof_Solution_5_Slides.pptx");
const skillDir = "/home/zxero/.codex/plugins/cache/openai-primary-runtime/presentations/26.1007.11041/skills/presentations";
const runtimePython = "/home/zxero/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3";
const { applyPresentationChartFont, finalizePresentation } = await import(
  pathToFileURL(path.join(skillDir, "container_tools/artifact_tool_utils.mjs")).href,
);

const W = 1280;
const H = 720;
const FONT = "Liberation Sans";
const CREAM = "#F8F7F2";
const WHITE = "#FFFFFF";
const BLUE = "#3559C7";
const BLUE_DARK = "#263F93";
const YELLOW = "#F2B84B";
const GREEN = "#2E7D5B";
const GREEN_SOFT = "#E7F5EC";
const TEXT = "#192033";
const MUTED = "#667085";
const OUTLINE = "#D9DEE8";

const assets = {
  hero: path.join(buildDir, "assets/bukal-hero.png"),
  icon: path.join(workspaceDir, "docs/branding/bukal-app-icon-concept.png"),
  passage: path.join(workspaceDir, "docs/references/ui/03-passage-selection.png"),
  setup: path.join(workspaceDir, "docs/references/ui/04-quiz-setup.png"),
  generating: path.join(workspaceDir, "docs/references/ui/05-generating.png"),
  quiz: path.join(workspaceDir, "docs/references/ui/06-quiz-answering.png"),
  history: path.join(workspaceDir, "docs/references/ui/09-history.png"),
};
const bytes = new Map();
for (const assetPath of Object.values(assets)) bytes.set(assetPath, await fs.readFile(assetPath));

const urls = {
  oecd: "https://www.oecd.org/en/publications/pisa-2022-results-volume-i-and-ii-country-notes_ed6fbcc5-en/philippines_a0882a2d-en.html",
  psa: "https://psa.gov.ph/content/percentage-households-internet-connection-increased-488-percent-2024-two-every-three",
  retrieval: "https://www.psychologicalscience.org/journals/psychological-science/j.1467-9280.2006.01693.x/",
};

await fs.mkdir(buildDir, { recursive: true });
await fs.mkdir(renderDir, { recursive: true });
await fs.mkdir(outputDir, { recursive: true });

const deck = Presentation.create({ slideSize: { width: W, height: H } });

function box(slide, x, y, w, h, fill = "none", radius = 0, line = "none") {
  return slide.shapes.add({
    geometry: radius ? "roundRect" : "rect",
    position: { left: x, top: y, width: w, height: h },
    fill,
    line: line === "none" ? { style: "solid", fill: "none", width: 0 } : { style: "solid", fill: line, width: 1 },
    ...(radius ? { borderRadius: radius } : {}),
  });
}

function line(slide, x, y, w, color = OUTLINE, thickness = 1) {
  return slide.shapes.add({
    geometry: "line",
    position: { left: x, top: y, width: w, height: 0 },
    fill: "none",
    line: { style: "solid", fill: color, width: thickness },
  });
}

function text(slide, value, x, y, w, h, options = {}) {
  const shape = slide.shapes.add({
    geometry: "textbox",
    position: { left: x, top: y, width: w, height: h },
    fill: options.fill ?? "none",
    line: { style: "solid", fill: options.line ?? "none", width: options.line ? 1 : 0 },
    ...(options.radius ? { borderRadius: options.radius } : {}),
  });
  shape.text = value;
  shape.text.style = {
    typeface: FONT,
    fontSize: options.size ?? 24,
    bold: options.bold ?? false,
    color: options.color ?? TEXT,
    alignment: options.align ?? "left",
    verticalAlignment: options.valign ?? "top",
    autoFit: "shrinkText",
    wrap: "square",
    insets: options.insets ?? 0,
    lineSpacing: options.lineSpacing ?? 1,
  };
  return shape;
}

function header(slide, number, title, subtitle = "") {
  text(slide, String(number).padStart(2, "0"), 72, 50, 50, 24, { size: 15, bold: true, color: BLUE });
  box(slide, 130, 57, 24, 4, YELLOW, 2);
  text(slide, title, 72, 88, 1100, 68, { size: 45, bold: true, color: TEXT, lineSpacing: 0.96 });
  if (subtitle) text(slide, subtitle, 74, 153, 1020, 42, { size: 22, color: MUTED });
}

function footer(slide, number, dark = false) {
  const c = dark ? "#B8C5F2" : MUTED;
  line(slide, 72, 684, 1136, dark ? "#4962B4" : OUTLINE, 1);
  text(slide, "BUKAL", 72, 690, 90, 16, { size: 11, bold: true, color: c });
  text(slide, String(number).padStart(2, "0"), 1170, 690, 38, 16, { size: 11, bold: true, color: c, align: "right" });
}

function citation(slide, label, url, x, y, w) {
  const shape = text(slide, "", x, y, w, 30, { size: 13, color: MUTED });
  shape.text.set([[
    {
      run: label,
      style: { typeface: FONT, fontSize: "10pt", color: MUTED },
      link: { uri: url, isExternal: true },
    },
  ]]);
}

function image(slide, assetPath, alt, x, y, w, h, fit = "cover", radius = 0) {
  return slide.images.add({
    blob: bytes.get(assetPath),
    contentType: "image/png",
    alt,
    fit,
    position: { left: x, top: y, width: w, height: h },
    ...(radius ? { geometry: "roundRect", borderRadius: radius } : {}),
  });
}

function phone(slide, assetPath, alt, x, y, w, h) {
  box(slide, x - 6, y - 6, w + 12, h + 12, WHITE, 24, OUTLINE).shadow = "shadow-md";
  return image(slide, assetPath, alt, x, y, w, h, "cover", 18);
}

function notes(slide, values) {
  slide.speakerNotes.text = values;
  slide.speakerNotes.setVisible(true);
}

// Slide 1: Cover
{
  const slide = deck.slides.add();
  slide.background.fill = CREAM;
  image(slide, assets.hero, "Illustration of a Filipino student studying from files on a phone", 0, 0, W, H);
  box(slide, 0, 0, 600, H, CREAM);
  image(slide, assets.icon, "Bukal app icon", 70, 52, 64, 64, "contain", 14);
  text(slide, "BUKAL", 150, 66, 200, 36, { size: 20, bold: true, color: BLUE });
  text(slide, "Turn the lesson on your phone into a quiz you can use offline", 70, 180, 500, 250, {
    size: 54,
    bold: true,
    color: TEXT,
    lineSpacing: 0.93,
  });
  box(slide, 70, 462, 94, 6, YELLOW, 3);
  text(slide, "A local Android study companion built around the learner's own files", 70, 493, 480, 74, {
    size: 24,
    color: MUTED,
  });
  notes(slide, [
    "Opening: Bukal turns a learner-owned file into local, source-linked quiz practice.",
    "Visual disclosure: Original AI-generated editorial illustration. It is not documentary photography.",
    "Product sources: docs/hackathon-plan.md and docs/quiz-types-and-profile.md.",
  ]);
}

// Slide 2: Problem
{
  const slide = deck.slides.add();
  slide.background.fill = CREAM;
  header(slide, 2, "The study-material problem");
  text(slide, "A learner can own the file and still lack a practical way to test recall.", 72, 192, 700, 100, {
    size: 37,
    bold: true,
    lineSpacing: 0.98,
  });
  const rows = [
    ["REREADING", "The lesson explains, but rarely asks the learner to retrieve."],
    ["QUIZ SETUP", "Writing questions and checking answers add more work before studying begins."],
    ["ONLINE DEPENDENCY", "A cloud workflow can stop when the connection drops."],
  ];
  let y = 330;
  rows.forEach(([label, body], index) => {
    text(slide, label, 78, y, 180, 24, { size: 13, bold: true, color: index === 2 ? GREEN : BLUE });
    text(slide, body, 270, y - 4, 480, 58, { size: 21, color: TEXT });
    if (index < 2) line(slide, 78, y + 74, 650, OUTLINE, 1);
    y += 105;
  });
  box(slide, 800, 188, 408, 404, BLUE_DARK, 28);
  text(slide, "PDF", 842, 232, 130, 62, { size: 43, bold: true, color: WHITE });
  text(slide, "DOCX", 1010, 232, 150, 62, { size: 43, bold: true, color: WHITE });
  text(slide, "PPTX", 842, 330, 150, 62, { size: 43, bold: true, color: "#C9D4FF" });
  text(slide, "TXT", 1030, 330, 120, 62, { size: 43, bold: true, color: "#C9D4FF" });
  line(slide, 842, 424, 316, "#5A70BE", 2);
  text(slide, "The content is available", 842, 465, 310, 34, { size: 22, bold: true, color: YELLOW });
  text(slide, "The practice still has to be created", 842, 512, 300, 58, { size: 20, color: WHITE });
  footer(slide, 2);
  notes(slide, [
    "This slide states the product problem. The next two slides provide the evidence.",
    "Product source: docs/hackathon-plan.md and docs/quiz-types-and-profile.md.",
  ]);
}

// Slide 3: Learning proof
{
  const slide = deck.slides.add();
  slide.background.fill = CREAM;
  header(slide, 3, "Reading proficiency in the Philippines", "PISA 2022 results for 15-year-old students");
  text(slide, "24%", 72, 234, 280, 118, { size: 86, bold: true, color: BLUE });
  text(slide, "reached Level 2 or higher in reading", 78, 350, 430, 65, { size: 26, bold: true });
  text(slide, "The OECD average was 74%.", 78, 436, 420, 40, { size: 22, color: MUTED });
  text(slide, "This does not prove demand for Bukal. It shows a large learning challenge where easier practice can help.", 78, 500, 450, 78, {
    size: 20,
    color: GREEN,
    bold: true,
  });
  const chart = slide.charts.add("bar", {
    position: { left: 610, top: 230, width: 560, height: 340 },
    categories: ["Philippines", "OECD average"],
    series: [{
      name: "Reached Level 2+",
      values: [24, 74],
      fill: BLUE,
      points: [{ idx: 0, fill: BLUE }, { idx: 1, fill: "#AAB8E8" }],
      valuesFormatCode: '0"%"',
    }],
    hasLegend: false,
    barOptions: { direction: "bar", grouping: "clustered", gapWidth: 56 },
    xAxis: {
      min: 0,
      max: 100,
      majorUnit: 25,
      numberFormatCode: '0"%"',
      textStyle: { fill: MUTED, fontSize: 13 },
      majorGridlines: { style: "solid", fill: OUTLINE, width: 1 },
      line: { style: "solid", fill: OUTLINE, width: 1 },
    },
    yAxis: {
      textStyle: { fill: TEXT, fontSize: 16, bold: true },
      line: { style: "solid", fill: "none", width: 0 },
      majorGridlines: null,
    },
    dataLabels: { showValue: true, position: "outEnd", textStyle: { fill: TEXT, fontSize: 16, bold: true } },
    chartFill: "none",
    chartLine: { style: "solid", fill: "none", width: 0 },
    plotAreaFill: "none",
    plotAreaLine: { style: "solid", fill: "none", width: 0 },
  });
  applyPresentationChartFont(chart, { fontFamily: FONT });
  citation(slide, "Source: OECD, PISA 2022 Philippines", urls.oecd, 72, 640, 430);
  footer(slide, 3);
  notes(slide, [
    "Source: OECD, PISA 2022 Results, country note for the Philippines.",
    urls.oecd,
    "Claim: 24% of students in the Philippines attained Level 2 or higher in reading, compared with an OECD average of 74%.",
    "The OECD states that Philippines data met PISA quality standards and were fit for reporting.",
  ]);
}

// Slide 4: Access and method proof
{
  const slide = deck.slides.add();
  slide.background.fill = CREAM;
  header(slide, 4, "Access and learning method");
  text(slide, "48.8%", 72, 210, 300, 110, { size: 78, bold: true, color: BLUE });
  text(slide, "of Philippine households had internet access at home in 2024", 78, 323, 470, 82, {
    size: 26,
    bold: true,
  });
  text(slide, "Offline study avoids adding another connection requirement after setup.", 78, 432, 470, 72, {
    size: 21,
    bold: true,
    color: GREEN,
  });
  citation(slide, "Source: PSA, 2024 NICTHS", urls.psa, 72, 548, 360);
  line(slide, 604, 205, 0, OUTLINE, 1);
  phone(slide, assets.quiz, "Bukal quiz answering UI reference", 868, 170, 220, 490);
  text(slide, "Self-testing supports retention", 620, 222, 220, 74, { size: 30, bold: true, color: TEXT });
  text(slide, "A controlled study using educational prose found that taking memory tests improved later retention compared with restudying.", 620, 322, 210, 150, {
    size: 20,
    color: MUTED,
    lineSpacing: 1.08,
  });
  text(slide, "Bukal turns the selected passage into that practice.", 620, 500, 220, 72, { size: 20, bold: true, color: BLUE });
  citation(slide, "Source: Roediger & Karpicke (2006)", urls.retrieval, 620, 604, 330);
  footer(slide, 4);
  notes(slide, [
    "Connectivity source: Philippine Statistics Authority, 2024 NICTHS preliminary results, released 21 July 2025.",
    urls.psa,
    "Claim: 48.8% of Philippine households had internet access at home in 2024.",
    "Learning source: Roediger and Karpicke (2006), Test-Enhanced Learning.",
    urls.retrieval,
    "Paraphrased claim: testing with educational prose improved later retention compared with restudying.",
    "UI image source: docs/references/ui/06-quiz-answering.png. It is a visual reference, not a runtime screenshot.",
  ]);
}

// Slide 5: Solution
{
  const slide = deck.slides.add();
  slide.background.fill = BLUE_DARK;
  text(slide, "05", 72, 46, 50, 24, { size: 15, bold: true, color: YELLOW });
  box(slide, 130, 53, 24, 4, YELLOW, 2);
  text(slide, "Bukal turns one lesson into active practice", 72, 80, 1040, 72, { size: 45, bold: true, color: WHITE });
  text(slide, "The learner stays close to the selected source from import to review.", 74, 145, 900, 40, { size: 22, color: "#C9D4FF" });
  const flow = [
    [assets.passage, "Choose a passage", "Exact source"],
    [assets.setup, "Pick quiz types", "Five supported types"],
    [assets.generating, "Generate locally", "After model setup"],
    [assets.history, "Review and retake", "Saved on the device"],
  ];
  const pw = 162;
  const ph = 360;
  const gap = 94;
  const start = 140;
  flow.forEach(([assetPath, label, sub], index) => {
    const x = start + index * (pw + gap);
    phone(slide, assetPath, `Bukal ${label} UI reference`, x, 220, pw, ph);
    text(slide, `${index + 1}  ${label}`, x - 26, 598, pw + 52, 28, { size: 17, bold: true, color: WHITE, align: "center" });
    text(slide, sub, x - 26, 630, pw + 52, 24, { size: 14, color: "#C9D4FF", align: "center" });
  });
  footer(slide, 5, true);
  notes(slide, [
    "Solution sources: docs/hackathon-plan.md; docs/quiz-types-and-profile.md; tools/context-factory/README.md.",
    "Core design: selected source passage, local question generation, five question types, local checking, source inspection, History and retakes.",
    "UI images are approved visual references from docs/references/ui. Their lesson names and scores are illustrative.",
  ]);
}

const stagingDir = path.join(workspaceDir, ".codex-finalizer");
await fs.mkdir(stagingDir, { recursive: true });
const candidatePath = path.join(stagingDir, "bukal-short-pitch-candidate.pptx");
await (await PresentationFile.exportPptx(deck)).save(candidatePath);

await finalizePresentation({
  explicitTotalSlideCount: 5,
  requiredNativeTableOwnerSlides: [],
  requiredNativeChartOwnerSlides: [3],
  materializeLiteralChartWorkbooks: true,
  nativeChartTargetApplication: "powerpoint",
  workspaceDir,
  candidatePath,
  finalPath,
  pythonExecutable: runtimePython,
  integrityValidatorPath: path.join(skillDir, "container_tools/inspect_presentation_package_integrity.py"),
  layoutValidatorPath: path.join(skillDir, "container_tools/inspect_presentation_layout_geometry.py"),
  layoutArgs: [
    "--expected-slide-size-emu",
    "12192000,6858000",
    "--validate-bullet-geometry",
    "--validate-heading-fit",
  ],
  fontPolicy: { basis: "design", families: [FONT] },
  verifyArtifactToolImport: true,
  receiptPath: path.join(stagingDir, "bukal-short-pitch.validation.json"),
});

const finalDeck = await PresentationFile.importPptx(await FileBlob.load(finalPath));
for (const [index, slide] of finalDeck.slides.items.entries()) {
  const png = await finalDeck.export({ slide, format: "png", scale: 1 });
  await fs.writeFile(path.join(renderDir, `slide-${String(index + 1).padStart(2, "0")}.png`), new Uint8Array(await png.arrayBuffer()));
}
const montage = await finalDeck.export({ format: "webp", montage: true });
await fs.writeFile(path.join(buildDir, "bukal-short-pitch-montage.webp"), new Uint8Array(await montage.arrayBuffer()));
console.log(finalPath);
