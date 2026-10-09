import fs from "node:fs/promises";
import path from "node:path";
import { pathToFileURL } from "node:url";
import {
  FileBlob,
  Presentation,
  PresentationFile,
} from "@oai/artifact-tool";

const workspaceDir = "/home/zxero/AndroidStudioProjects/locquiz";
const buildDir = path.join(workspaceDir, ".codex-ppt-build");
const renderDir = path.join(buildDir, "render-final");
const outputDir = path.join(workspaceDir, ".codex-ppt-output");
const finalPath = path.join(
  outputDir,
  "Bukal_Problem_Solution_Pitch_2026-10-09_v4.pptx",
);
const skillDir =
  "/home/zxero/.codex/plugins/cache/openai-primary-runtime/presentations/26.1007.11041/skills/presentations";
const runtimePython =
  "/home/zxero/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3";

const { applyPresentationChartFont, finalizePresentation } = await import(
  pathToFileURL(path.join(skillDir, "container_tools/artifact_tool_utils.mjs")).href
);

const W = 1280;
const H = 720;
const FONT = "Liberation Sans";
const CREAM = "#F8F7F2";
const WHITE = "#FFFFFF";
const BLUE = "#3559C7";
const BLUE_DARK = "#263F93";
const BLUE_SOFT = "#EEF2FF";
const YELLOW = "#F2B84B";
const GREEN = "#2E7D5B";
const GREEN_SOFT = "#E7F5EC";
const RED = "#C74B50";
const TEXT = "#192033";
const MUTED = "#667085";
const OUTLINE = "#D9DEE8";
const FONT_POLICY = { basis: "design", families: [FONT] };

const assets = {
  hero: path.join(buildDir, "assets/bukal-hero.png"),
  icon: path.join(workspaceDir, "docs/branding/bukal-app-icon-concept.png"),
  passage: path.join(workspaceDir, "docs/references/ui/03-passage-selection.png"),
  setup: path.join(workspaceDir, "docs/references/ui/04-quiz-setup.png"),
  generating: path.join(workspaceDir, "docs/references/ui/05-generating.png"),
  quiz: path.join(workspaceDir, "docs/references/ui/06-quiz-answering.png"),
  checking: path.join(workspaceDir, "docs/references/ui/07-ai-checking.png"),
  history: path.join(workspaceDir, "docs/references/ui/09-history.png"),
  profile: path.join(workspaceDir, "docs/references/ui/10-profile.png"),
};

const assetBytes = new Map();
for (const assetPath of Object.values(assets)) {
  assetBytes.set(assetPath, await fs.readFile(assetPath));
}

const sources = {
  oecd:
    "https://www.oecd.org/en/publications/pisa-2022-results-volume-i-and-ii-country-notes_ed6fbcc5-en/philippines_a0882a2d-en.html",
  psa:
    "https://psa.gov.ph/content/percentage-households-internet-connection-increased-488-percent-2024-two-every-three",
  retrieval:
    "https://www.psychologicalscience.org/journals/psychological-science/j.1467-9280.2006.01693.x/",
  ibm: "https://www.ibm.com/granite/docs/models/embedding",
};

await fs.mkdir(buildDir, { recursive: true });
await fs.mkdir(renderDir, { recursive: true });
await fs.mkdir(outputDir, { recursive: true });

const p = Presentation.create({ slideSize: { width: W, height: H } });

function addBox(slide, x, y, w, h, fill = "none", radius = 0, line = "none") {
  return slide.shapes.add({
    geometry: radius ? "roundRect" : "rect",
    position: { left: x, top: y, width: w, height: h },
    fill,
    line:
      line === "none"
        ? { style: "solid", fill: "none", width: 0 }
        : { style: "solid", fill: line, width: 1 },
    ...(radius ? { borderRadius: radius } : {}),
  });
}

function addLine(slide, x, y, w, color = OUTLINE, thickness = 1) {
  return slide.shapes.add({
    geometry: "line",
    position: { left: x, top: y, width: w, height: 0 },
    fill: "none",
    line: { style: "solid", fill: color, width: thickness },
  });
}

function addText(slide, text, x, y, w, h, opts = {}) {
  const shape = slide.shapes.add({
    geometry: "textbox",
    position: { left: x, top: y, width: w, height: h },
    fill: opts.fill ?? "none",
    line: opts.line
      ? { style: "solid", fill: opts.line, width: opts.lineWidth ?? 1 }
      : { style: "solid", fill: "none", width: 0 },
    ...(opts.radius ? { borderRadius: opts.radius } : {}),
  });
  shape.text = text;
  shape.text.style = {
    typeface: FONT,
    fontSize: opts.size ?? 24,
    bold: opts.bold ?? false,
    color: opts.color ?? TEXT,
    alignment: opts.align ?? "left",
    verticalAlignment: opts.valign ?? "top",
    autoFit: opts.autoFit ?? "shrinkText",
    wrap: "square",
    insets: opts.insets ?? 0,
    lineSpacing: opts.lineSpacing ?? 1.0,
  };
  return shape;
}

function addStructuredText(slide, value, x, y, w, h, opts = {}) {
  const shape = addText(slide, "", x, y, w, h, opts);
  shape.text = value;
  shape.text.style = {
    typeface: FONT,
    fontSize: opts.size ?? 24,
    color: opts.color ?? TEXT,
    alignment: opts.align ?? "left",
    verticalAlignment: opts.valign ?? "top",
    autoFit: opts.autoFit ?? "shrinkText",
    insets: opts.insets ?? 0,
    lineSpacing: opts.lineSpacing ?? 1.0,
  };
  return shape;
}

function addSectionHeader(slide, number, title, subtitle = "") {
  addText(slide, String(number).padStart(2, "0"), 72, 52, 56, 26, {
    size: 15,
    bold: true,
    color: BLUE,
  });
  addBox(slide, 130, 58, 24, 4, YELLOW, 2);
  addText(slide, title, 72, 89, 1110, 70, {
    size: 45,
    bold: true,
    color: TEXT,
    lineSpacing: 0.95,
  });
  if (subtitle) {
    addText(slide, subtitle, 74, 154, 1040, 48, {
      size: 23,
      color: MUTED,
    });
  }
}

function addFooter(slide, number, dark = false) {
  const color = dark ? "#B8C5F2" : MUTED;
  addLine(slide, 72, 684, 1136, dark ? "#4962B4" : OUTLINE, 1);
  addText(slide, "BUKAL", 72, 690, 90, 18, {
    size: 11,
    bold: true,
    color,
  });
  addText(slide, String(number).padStart(2, "0"), 1170, 690, 38, 18, {
    size: 11,
    bold: true,
    color,
    align: "right",
  });
}

function addCitation(slide, label, url, x, y, w, color = MUTED) {
  const shape = addText(slide, "", x, y, w, 34, {
    size: 13,
    color,
  });
  shape.text.set([
    [
      {
        run: label,
        style: { typeface: FONT, fontSize: "10pt", color },
        link: { uri: url, isExternal: true },
      },
    ],
  ]);
  return shape;
}

function addPhone(slide, imagePath, x, y, w, h, alt) {
  addBox(slide, x - 7, y - 7, w + 14, h + 14, WHITE, 26, OUTLINE).shadow =
    "shadow-md";
  return slide.images.add({
    blob: assetBytes.get(imagePath),
    contentType: "image/png",
    alt,
    fit: "cover",
    position: { left: x, top: y, width: w, height: h },
    geometry: "roundRect",
    borderRadius: 20,
  });
}

function setNotes(slide, lines) {
  slide.speakerNotes.text = lines;
  slide.speakerNotes.setVisible(true);
}

// 1. Cover
{
  const slide = p.slides.add();
  slide.background.fill = CREAM;
  slide.images.add({
    blob: assetBytes.get(assets.hero),
    contentType: "image/png",
    alt: "Illustration of a Filipino student studying from lesson files on a phone",
    fit: "cover",
    position: { left: 0, top: 0, width: W, height: H },
  });
  addBox(slide, 0, 0, 600, H, CREAM);
  slide.images.add({
    blob: assetBytes.get(assets.icon),
    contentType: "image/png",
    alt: "Bukal app icon",
    fit: "contain",
    position: { left: 70, top: 52, width: 64, height: 64 },
    geometry: "roundRect",
    borderRadius: 14,
  });
  addText(slide, "BUKAL", 150, 66, 200, 40, {
    size: 20,
    bold: true,
    color: BLUE,
  });
  addText(slide, "Turn the lesson on your phone into a quiz you can use offline", 70, 180, 500, 250, {
    size: 54,
    bold: true,
    color: TEXT,
    lineSpacing: 0.93,
  });
  addBox(slide, 70, 462, 94, 6, YELLOW, 3);
  addText(
    slide,
    "A local-first Android study companion for source-grounded practice",
    70,
    490,
    470,
    76,
    { size: 24, color: MUTED, lineSpacing: 1.05 },
  );
  addText(slide, "Problem and solution pitch", 70, 635, 280, 28, {
    size: 15,
    bold: true,
    color: BLUE,
  });
  setNotes(slide, [
    "Opening: Bukal turns a learner-owned lesson file into local, source-linked practice.",
    "Visual disclosure: Original AI-generated editorial illustration. It is not documentary photography.",
    "Product sources: docs/hackathon-plan.md; docs/quiz-types-and-profile.md; tools/context-factory/README.md.",
  ]);
}

// 2. Problem
{
  const slide = p.slides.add();
  slide.background.fill = CREAM;
  addSectionHeader(slide, 2, "The study-material problem");
  addText(
    slide,
    "A learner can own the file and still lack a practical way to test recall.",
    72,
    196,
    660,
    110,
    { size: 35, bold: true, color: TEXT, lineSpacing: 0.96 },
  );
  const items = [
    ["01", "Rereading stays passive", "The lesson explains. It rarely asks the learner to retrieve."],
    ["02", "Writing practice takes time", "Questions, answer keys, and checking all become extra work."],
    ["03", "Cloud study adds a dependency", "When the workflow is online, a weak connection can stop the session."],
  ];
  let y = 330;
  for (const [n, head, body] of items) {
    addText(slide, n, 76, y + 4, 46, 28, { size: 14, bold: true, color: BLUE });
    addText(slide, head, 140, y, 420, 34, { size: 24, bold: true, color: TEXT });
    addText(slide, body, 140, y + 38, 500, 48, { size: 18, color: MUTED });
    y += 104;
  }
  addBox(slide, 770, 188, 438, 414, BLUE_DARK, 28);
  addText(slide, "PDF", 820, 232, 150, 74, { size: 48, bold: true, color: WHITE });
  addText(slide, "DOCX", 1010, 232, 160, 74, { size: 48, bold: true, color: WHITE });
  addText(slide, "PPTX", 820, 338, 160, 74, { size: 48, bold: true, color: "#C9D4FF" });
  addText(slide, "TXT", 1030, 338, 130, 74, { size: 48, bold: true, color: "#C9D4FF" });
  addLine(slide, 820, 445, 338, "#5A70BE", 2);
  addText(slide, "Content is available", 820, 478, 330, 42, {
    size: 22,
    bold: true,
    color: YELLOW,
  });
  addText(slide, "Active practice still needs to be created", 820, 523, 330, 58, {
    size: 20,
    color: WHITE,
  });
  addFooter(slide, 2);
  setNotes(slide, [
    "Problem framing is a product hypothesis grounded in the Bukal workflow and the evidence on the next slides.",
    "Do not present this slide alone as proof of market demand. It defines the task Bukal addresses.",
    "Product source: docs/hackathon-plan.md and docs/quiz-types-and-profile.md.",
  ]);
}

// 3. PISA evidence
{
  const slide = p.slides.add();
  slide.background.fill = CREAM;
  addSectionHeader(
    slide,
    3,
    "Reading proficiency in the Philippines",
    "PISA 2022 results for 15-year-old students",
  );
  addText(slide, "24%", 72, 235, 280, 122, { size: 88, bold: true, color: BLUE });
  addText(slide, "reached Level 2 or higher in reading", 78, 350, 440, 66, {
    size: 26,
    bold: true,
    color: TEXT,
  });
  addText(
    slide,
    "At this level, students can identify a main idea, locate information under explicit criteria, and reflect on a text when directed.",
    78,
    430,
    470,
    112,
    { size: 20, color: MUTED, lineSpacing: 1.1 },
  );
  const chart = slide.charts.add("bar", {
    position: { left: 610, top: 218, width: 560, height: 350 },
    categories: ["Philippines", "OECD average"],
    series: [
      {
        name: "Reached Level 2+",
        values: [24, 74],
        fill: BLUE,
        points: [
          { idx: 0, fill: BLUE },
          { idx: 1, fill: "#AAB8E8" },
        ],
        valuesFormatCode: '0"%"',
      },
    ],
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
    dataLabels: {
      showValue: true,
      position: "outEnd",
      textStyle: { fill: TEXT, fontSize: 16, bold: true },
    },
    chartFill: "none",
    chartLine: { style: "solid", fill: "none", width: 0 },
    plotAreaFill: "none",
    plotAreaLine: { style: "solid", fill: "none", width: 0 },
  });
  applyPresentationChartFont(chart, { fontFamily: FONT });
  addText(
    slide,
    "Bukal cannot solve a national learning gap alone. It addresses one concrete part: making active practice easier to start from the material already in hand.",
    610,
    584,
    560,
    60,
    { size: 18, color: MUTED },
  );
  addCitation(slide, "Source: OECD, PISA 2022 Philippines country note", sources.oecd, 72, 640, 540);
  addFooter(slide, 3);
  setNotes(slide, [
    "Source: OECD, PISA 2022 Results, country note for the Philippines.",
    sources.oecd,
    "Claim used: 24% of students in the Philippines attained Level 2 or higher in reading, compared with an OECD average of 74%.",
    "The OECD states that Philippines data met PISA quality standards and were fit for reporting.",
  ]);
}

// 4. Connectivity evidence
{
  const slide = p.slides.add();
  slide.background.fill = CREAM;
  addSectionHeader(
    slide,
    4,
    "Uneven home internet access",
    "2024 National ICT Household Survey, preliminary results",
  );
  addText(slide, "48.8%", 72, 226, 310, 120, { size: 82, bold: true, color: BLUE });
  addText(slide, "of Philippine households had internet access at home", 78, 344, 420, 78, {
    size: 26,
    bold: true,
    color: TEXT,
  });
  addText(slide, "That leaves 51.2% without home access in the survey.", 78, 442, 430, 58, {
    size: 21,
    color: MUTED,
  });
  addText(slide, "An offline study mode protects the session when connectivity disappears.", 78, 520, 460, 72, {
    size: 23,
    bold: true,
    color: GREEN,
  });
  const chart = slide.charts.add("bar", {
    position: { left: 620, top: 220, width: 550, height: 360 },
    categories: ["NCR", "Philippines", "Zamboanga Peninsula"],
    series: [
      {
        name: "Households with home internet",
        values: [68.7, 48.8, 21.2],
        fill: BLUE,
        points: [
          { idx: 0, fill: "#AAB8E8" },
          { idx: 1, fill: BLUE },
          { idx: 2, fill: YELLOW },
        ],
        valuesFormatCode: '0.0"%"',
      },
    ],
    hasLegend: false,
    barOptions: { direction: "bar", grouping: "clustered", gapWidth: 46 },
    xAxis: {
      min: 0,
      max: 80,
      majorUnit: 20,
      numberFormatCode: '0"%"',
      textStyle: { fill: MUTED, fontSize: 13 },
      majorGridlines: { style: "solid", fill: OUTLINE, width: 1 },
      line: { style: "solid", fill: OUTLINE, width: 1 },
    },
    yAxis: {
      textStyle: { fill: TEXT, fontSize: 14, bold: true },
      line: { style: "solid", fill: "none", width: 0 },
      majorGridlines: null,
    },
    dataLabels: {
      showValue: true,
      position: "outEnd",
      textStyle: { fill: TEXT, fontSize: 15, bold: true },
    },
    chartFill: "none",
    chartLine: { style: "solid", fill: "none", width: 0 },
    plotAreaFill: "none",
    plotAreaLine: { style: "solid", fill: "none", width: 0 },
  });
  applyPresentationChartFont(chart, { fontFamily: FONT });
  addCitation(slide, "Source: PSA, 2024 NICTHS", sources.psa, 72, 640, 420);
  addFooter(slide, 4);
  setNotes(slide, [
    "Source: Philippine Statistics Authority, 2024 National Information and Communications Technology Household Survey preliminary results, released 21 July 2025.",
    sources.psa,
    "Claims used: 48.8% national household internet access; NCR 68.7%; Zamboanga Peninsula 21.2%.",
    "The 51.2% figure is the arithmetic complement of 48.8%.",
  ]);
}

// 5. Retrieval practice
{
  const slide = p.slides.add();
  slide.background.fill = CREAM;
  addSectionHeader(slide, 5, "Why quizzes matter");
  addText(
    slide,
    "Taking a memory test can improve later retention, not only measure it.",
    72,
    205,
    610,
    128,
    { size: 39, bold: true, color: TEXT, lineSpacing: 0.96 },
  );
  addText(
    slide,
    "Roediger and Karpicke tested educational prose and found a long-term benefit from retrieval practice compared with restudying.",
    74,
    360,
    555,
    104,
    { size: 22, color: MUTED, lineSpacing: 1.1 },
  );
  addBox(slide, 74, 492, 520, 86, GREEN_SOFT, 18);
  addText(slide, "Bukal removes the setup work", 98, 511, 470, 30, {
    size: 22,
    bold: true,
    color: GREEN,
  });
  addText(slide, "The selected passage becomes a ready-to-answer quiz.", 98, 547, 470, 26, {
    size: 17,
    color: TEXT,
  });
  addPhone(slide, assets.quiz, 842, 170, 220, 490, "Bukal quiz answering UI reference");
  addCitation(slide, "Source: Roediger & Karpicke (2006)", sources.retrieval, 72, 640, 420);
  addFooter(slide, 5);
  setNotes(slide, [
    "Research source: Roediger, H. L. III, and Karpicke, J. D. (2006), Test-Enhanced Learning.",
    sources.retrieval,
    "Paraphrased claim: taking memory tests can enhance later retention; the study used educationally relevant materials.",
    "UI image source: docs/references/ui/06-quiz-answering.png. It is an approved visual reference, not a runtime screenshot.",
  ]);
}

// 6. Product flow
{
  const slide = p.slides.add();
  slide.background.fill = BLUE_DARK;
  addText(slide, "06", 72, 46, 56, 28, { size: 15, bold: true, color: YELLOW });
  addBox(slide, 130, 53, 24, 4, YELLOW, 2);
  addText(slide, "The Bukal study flow", 72, 82, 1000, 70, {
    size: 45,
    bold: true,
    color: WHITE,
  });
  addText(slide, "The learner stays in one focused path from source to review.", 74, 144, 900, 44, {
    size: 22,
    color: "#C9D4FF",
  });
  const flow = [
    [assets.passage, "1  Choose a passage", "Select the exact source"],
    [assets.setup, "2  Pick question types", "One to five types"],
    [assets.generating, "3  Generate locally", "Fresh model sessions"],
    [assets.history, "4  Retake from History", "Saved on the device"],
  ];
  const phoneW = 162;
  const phoneH = 360;
  const gap = 94;
  const startX = 140;
  flow.forEach(([img, label, sub], i) => {
    const x = startX + i * (phoneW + gap);
    addPhone(slide, img, x, 220, phoneW, phoneH, label);
    addText(slide, label, x - 24, 598, phoneW + 48, 30, {
      size: 17,
      bold: true,
      color: WHITE,
      align: "center",
    });
    addText(slide, sub, x - 24, 630, phoneW + 48, 25, {
      size: 14,
      color: "#C9D4FF",
      align: "center",
    });
  });
  addFooter(slide, 6, true);
  setNotes(slide, [
    "Product flow source: docs/hackathon-plan.md and docs/quiz-types-and-profile.md.",
    "UI images are approved visual references from docs/references/ui. They are not runtime screenshots and contain illustrative lesson data.",
    "The current app flow connects all ten required destinations. See tools/context-factory/README.md.",
  ]);
}

// 7. Local and grounded
{
  const slide = p.slides.add();
  slide.background.fill = CREAM;
  addSectionHeader(slide, 7, "Local, grounded, inspectable");
  addText(
    slide,
    "Bukal is designed around the learner's selected passage, not an open-ended chat.",
    72,
    196,
    620,
    92,
    { size: 31, bold: true, color: TEXT, lineSpacing: 0.98 },
  );
  const principles = [
    ["ON DEVICE", "After model setup, generation, checking, search, and history are designed to run locally."],
    ["SOURCE LINKED", "Every question points back to the selected passage and its stable source ID."],
    ["INSPECTABLE", "The learner can reopen the passage and request an explanation only when needed."],
  ];
  let y = 322;
  principles.forEach(([label, body], i) => {
    addText(slide, label, 78, y, 140, 26, {
      size: 13,
      bold: true,
      color: i === 0 ? GREEN : BLUE,
    });
    addText(slide, body, 220, y - 2, 490, 62, { size: 19, color: TEXT });
    if (i < principles.length - 1) addLine(slide, 78, y + 76, 612, OUTLINE, 1);
    y += 103;
  });
  addPhone(slide, assets.checking, 866, 170, 220, 490, "Bukal local answer checking UI reference");
  addText(slide, "No account. No backend. No learner-data upload.", 760, 610, 430, 42, {
    size: 18,
    bold: true,
    color: GREEN,
    align: "center",
  });
  addFooter(slide, 7);
  setNotes(slide, [
    "Product contract sources: docs/hackathon-plan.md; docs/quiz-types-and-profile.md; tools/context-factory/README.md.",
    "Important limitation: full end-to-end airplane-mode sign-off remains pending and appears on the build-status slide.",
    "UI image source: docs/references/ui/07-ai-checking.png. It is an approved visual reference, not a runtime screenshot.",
  ]);
}

// 8. Toolkit
{
  const slide = p.slides.add();
  slide.background.fill = CREAM;
  addSectionHeader(slide, 8, "A fuller self-study toolkit");
  addPhone(slide, assets.setup, 72, 190, 184, 410, "Bukal quiz type selection UI reference");
  addPhone(slide, assets.profile, 292, 190, 184, 410, "Bukal local activity profile UI reference");
  addText(slide, "Five question types", 540, 205, 360, 34, { size: 25, bold: true, color: BLUE });
  addText(
    slide,
    "Multiple choice, fill in the blank, identification, matching, and explanation",
    540,
    250,
    590,
    72,
    { size: 21, color: TEXT },
  );
  addLine(slide, 540, 343, 610, OUTLINE, 1);
  addText(slide, "Meaning-based search", 540, 373, 360, 34, { size: 25, bold: true, color: BLUE });
  addText(
    slide,
    "Granite embeddings find related text across retained lesson chunks and open the original passage.",
    540,
    418,
    590,
    68,
    { size: 21, color: TEXT },
  );
  addLine(slide, 540, 509, 610, OUTLINE, 1);
  addText(slide, "Local continuity", 540, 539, 360, 34, { size: 25, bold: true, color: BLUE });
  addText(
    slide,
    "History supports retakes. Profile turns completed quizzes into a yearly activity view and streaks.",
    540,
    584,
    590,
    62,
    { size: 21, color: TEXT },
  );
  addFooter(slide, 8);
  setNotes(slide, [
    "Feature contract sources: docs/quiz-types-and-profile.md and docs/hackathon-plan.md.",
    "Granite is used for semantic retrieval, not answer grading. The selected local quiz model assigns open-answer verdicts.",
    "The approved UI reference for Profile uses illustrative activity data. Room-backed Profile wiring remains pending.",
    "UI images: docs/references/ui/04-quiz-setup.png and docs/references/ui/10-profile.png.",
    "Embedding model reference: " + sources.ibm,
  ]);
}

// 9. Positioning
{
  const slide = p.slides.add();
  slide.background.fill = CREAM;
  addSectionHeader(slide, 9, "Bukal's product position");
  const x0 = 72;
  const colXs = [390, 580, 770, 960];
  const colW = 180;
  addText(slide, "Typical workflow", x0, 214, 270, 40, { size: 19, bold: true, color: MUTED });
  ["Rereading", "Manual flashcards", "Cloud AI", "BUKAL"].forEach((name, i) => {
    addText(slide, name, colXs[i], 206, colW, 50, {
      size: 18,
      bold: true,
      color: i === 3 ? WHITE : TEXT,
      align: "center",
      valign: "middle",
      fill: i === 3 ? BLUE : "none",
      radius: i === 3 ? 12 : 0,
    });
  });
  const rows = [
    ["Question setup effort", "None", "High", "Low", "Low"],
    ["Active recall", "No", "Yes", "Yes", "Yes"],
    ["Works after internet loss", "Yes", "Yes", "Usually no", "After setup"],
    ["Lesson stays on device", "Yes", "Yes", "Depends", "Yes"],
    ["Return to exact passage", "Manual", "Manual", "Varies", "Built in"],
    ["Mixed question types", "No", "Depends", "Often", "Five"],
  ];
  let y = 278;
  rows.forEach((row, r) => {
    if (r % 2 === 0) addBox(slide, x0, y - 8, 1068, 52, WHITE, 8);
    addText(slide, row[0], x0 + 14, y, 286, 30, { size: 17, bold: true, color: TEXT });
    for (let i = 0; i < 4; i++) {
      addText(slide, row[i + 1], colXs[i], y, colW, 30, {
        size: 17,
        bold: i === 3,
        color: i === 3 ? BLUE : MUTED,
        align: "center",
      });
    }
    y += 58;
  });
  addText(
    slide,
    "Feature availability varies by product. This compares common study workflows rather than named competitors.",
    72,
    640,
    900,
    26,
    { size: 13, color: MUTED },
  );
  addFooter(slide, 9);
  setNotes(slide, [
    "This is a product-positioning comparison, not a market-share study or a named-competitor audit.",
    "Bukal attributes come from docs/hackathon-plan.md and docs/quiz-types-and-profile.md.",
    "Cloud and flashcard columns describe common workflows; availability varies by product, as disclosed on-slide.",
  ]);
}

// 10. Market
{
  const slide = p.slides.add();
  slide.background.fill = CREAM;
  addSectionHeader(slide, 10, "Initial market");
  addText(slide, "Start with the learner who already has the file", 72, 205, 610, 88, {
    size: 37,
    bold: true,
    color: TEXT,
    lineSpacing: 0.98,
  });
  addText(
    slide,
    "Senior high and college students who receive modules, handouts, slides, and readings on Android phones",
    74,
    315,
    580,
    84,
    { size: 23, color: MUTED },
  );
  addBox(slide, 72, 444, 560, 150, BLUE_SOFT, 22);
  addText(slide, "Trigger moment", 100, 468, 220, 30, {
    size: 18,
    bold: true,
    color: BLUE,
  });
  addText(
    slide,
    "A quiz is coming. The lesson is already downloaded. The learner needs practice now, even if the connection is weak.",
    100,
    508,
    490,
    68,
    { size: 20, color: TEXT },
  );
  addText(slide, "Go-to-market hypothesis", 730, 211, 400, 36, {
    size: 24,
    bold: true,
    color: BLUE,
  });
  const go = [
    ["Campus demos", "Show the complete file-to-quiz flow in minutes."],
    ["Peer distribution", "Student organizations, tutors, and study groups create trust."],
    ["Prepared lesson packs", "Use openly licensed sample files for the first successful quiz."],
    ["Android-first rollout", "Test on the devices and connectivity patterns students already use."],
  ];
  let gy = 270;
  go.forEach(([head, body], i) => {
    addText(slide, String(i + 1), 730, gy, 34, 34, {
      size: 16,
      bold: true,
      color: WHITE,
      align: "center",
      valign: "middle",
      fill: i === 2 ? YELLOW : BLUE,
      radius: 17,
    });
    addText(slide, head, 782, gy - 2, 350, 30, { size: 21, bold: true, color: TEXT });
    addText(slide, body, 782, gy + 32, 390, 46, { size: 17, color: MUTED });
    gy += 90;
  });
  addText(slide, "HYPOTHESIS TO VALIDATE", 730, 632, 300, 24, {
    size: 12,
    bold: true,
    color: RED,
  });
  addFooter(slide, 10);
  setNotes(slide, [
    "This slide is explicitly a go-to-market hypothesis, not validated demand.",
    "Recommended validation: interview target students, observe study sessions, and measure whether Bukal saves setup time and increases completed practice.",
    "Product inputs and Android scope come from the repository documents.",
  ]);
}

// 11. Build status
{
  const slide = p.slides.add();
  slide.background.fill = CREAM;
  addSectionHeader(slide, 11, "Current build status", "The core loop is implemented; final device proof remains");
  slide.images.add({
    blob: assetBytes.get(assets.icon),
    contentType: "image/png",
    alt: "Bukal app icon",
    fit: "contain",
    position: { left: 72, top: 208, width: 150, height: 150 },
    geometry: "roundRect",
    borderRadius: 28,
  });
  addText(slide, "Verified now", 270, 211, 270, 34, { size: 24, bold: true, color: GREEN });
  addStructuredText(
    slide,
    [
      { bulletCharacter: "•", marginLeft: 20, indent: -12, runs: ["Unit-test task: BUILD SUCCESSFUL on 9 Oct 2026"] },
      { bulletCharacter: "•", marginLeft: 20, indent: -12, runs: ["Granite embedding and Room persistence passed on a Xiaomi 23049PCD8G, Android 15"] },
      { bulletCharacter: "•", marginLeft: 20, indent: -12, runs: ["Focused device test runtime: 3.944 seconds"] },
    ],
    270,
    260,
    520,
    180,
    { size: 19, color: TEXT, lineSpacing: 1.05 },
  );
  addText(slide, "Implemented in the app", 72, 452, 300, 34, { size: 24, bold: true, color: BLUE });
  addText(
    slide,
    "Model setup and verification  •  TXT/PDF/DOCX/PPTX import  •  five typed questions  •  local checking  •  source evidence  •  Room-backed History  •  Granite indexing and search tester",
    72,
    500,
    680,
    112,
    { size: 20, color: TEXT, lineSpacing: 1.08 },
  );
  addBox(slide, 830, 210, 360, 408, WHITE, 24, OUTLINE).shadow = "shadow-sm";
  addText(slide, "Next proof points", 862, 242, 300, 34, { size: 24, bold: true, color: YELLOW });
  addStructuredText(
    slide,
    [
      { bulletCharacter: "•", marginLeft: 20, indent: -12, runs: ["Full quiz-model inference on the presentation phone"] },
      { bulletCharacter: "•", marginLeft: 20, indent: -12, runs: ["Learner-facing Home search and retrieval benchmark"] },
      { bulletCharacter: "•", marginLeft: 20, indent: -12, runs: ["Room-backed yearly Profile and streaks"] },
      { bulletCharacter: "•", marginLeft: 20, indent: -12, runs: ["Complete airplane-mode demonstration and hint verification"] },
    ],
    862,
    300,
    286,
    260,
    { size: 19, color: TEXT, lineSpacing: 1.08 },
  );
  addFooter(slide, 11);
  setNotes(slide, [
    "Current unit-test verification run: ./gradlew testDebugUnitTest, BUILD SUCCESSFUL on 2026-10-09.",
    "Repository status source: tools/context-factory/README.md and docs/implementation-checklist.md.",
    "The focused connected-device test verified one Granite embedding plus Room persistence; it did not verify full query retrieval or end-to-end quiz generation.",
    "Do not pitch the product as fully complete until the listed device proof points pass.",
  ]);
}

// 12. Close
{
  const slide = p.slides.add();
  slide.background.fill = BLUE_DARK;
  slide.images.add({
    blob: assetBytes.get(assets.icon),
    contentType: "image/png",
    alt: "Bukal app icon",
    fit: "contain",
    position: { left: 876, top: 128, width: 250, height: 250 },
    geometry: "roundRect",
    borderRadius: 52,
  });
  addText(slide, "The lesson stays yours", 72, 126, 670, 90, {
    size: 55,
    bold: true,
    color: WHITE,
  });
  addText(
    slide,
    "Bukal turns learner-owned files into active practice, keeps the source within reach, and is designed to continue after the connection drops.",
    72,
    246,
    690,
    150,
    { size: 28, color: "#DCE4FF", lineSpacing: 1.08 },
  );
  addBox(slide, 72, 435, 610, 5, YELLOW, 3);
  addText(slide, "Demo path", 72, 474, 160, 30, { size: 17, bold: true, color: YELLOW });
  addText(slide, "Import  /  choose  /  generate  /  answer  /  reopen the source", 72, 516, 750, 42, {
    size: 22,
    bold: true,
    color: WHITE,
  });
  addText(slide, "BUKAL", 72, 642, 170, 34, { size: 19, bold: true, color: "#B8C5F2" });
  addText(slide, "Local-first study practice", 876, 409, 250, 34, {
    size: 18,
    bold: true,
    color: YELLOW,
    align: "center",
  });
  setNotes(slide, [
    "Close by offering the live demo path. Do not claim complete airplane-mode verification until the final proof points pass.",
    "Product sources: docs/hackathon-plan.md; docs/quiz-types-and-profile.md; tools/context-factory/README.md.",
  ]);
}

const stagingDir = path.join(workspaceDir, ".codex-finalizer");
await fs.mkdir(stagingDir, { recursive: true });
const candidatePath = path.join(stagingDir, "bukal-pitch-candidate-v4.pptx");
await (await PresentationFile.exportPptx(p)).save(candidatePath);

const result = await finalizePresentation({
  explicitTotalSlideCount: 12,
  requiredNativeTableOwnerSlides: [],
  requiredNativeChartOwnerSlides: [3, 4],
  materializeLiteralChartWorkbooks: true,
  nativeChartTargetApplication: "powerpoint",
  workspaceDir,
  candidatePath,
  finalPath,
  pythonExecutable: runtimePython,
  integrityValidatorPath: path.join(
    skillDir,
    "container_tools/inspect_presentation_package_integrity.py",
  ),
  layoutValidatorPath: path.join(
    skillDir,
    "container_tools/inspect_presentation_layout_geometry.py",
  ),
  layoutArgs: [
    "--expected-slide-size-emu",
    "12192000,6858000",
    "--validate-bullet-geometry",
    "--validate-heading-fit",
  ],
  fontPolicy: FONT_POLICY,
  verifyArtifactToolImport: true,
  receiptPath: path.join(stagingDir, "bukal-pitch-v4.validation.json"),
});

const finalDeck = await PresentationFile.importPptx(await FileBlob.load(finalPath));
for (const [index, slide] of finalDeck.slides.items.entries()) {
  const stem = `slide-${String(index + 1).padStart(2, "0")}`;
  const png = await finalDeck.export({ slide, format: "png", scale: 1 });
  await fs.writeFile(path.join(renderDir, `${stem}.png`), new Uint8Array(await png.arrayBuffer()));
}
const montage = await finalDeck.export({ format: "webp", montage: true });
await fs.writeFile(
  path.join(buildDir, "bukal-pitch-montage.webp"),
  new Uint8Array(await montage.arrayBuffer()),
);

console.log(JSON.stringify({ finalPath, result }, null, 2));
