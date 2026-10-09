import fs from "node:fs/promises";
import path from "node:path";
import { FileBlob, PresentationFile } from "@oai/artifact-tool";

const workspace = "/home/zxero/AndroidStudioProjects/locquiz";
const deckPath = path.join(
  workspace,
  ".codex-ppt-output/Bukal_Problem_Solution_Pitch_2026-10-09_v4.pptx",
);
const renderDir = path.join(workspace, ".codex-ppt-build/render-final");
await fs.mkdir(renderDir, { recursive: true });
const deck = await PresentationFile.importPptx(await FileBlob.load(deckPath));
for (const [index, slide] of deck.slides.items.entries()) {
  const png = await deck.export({ slide, format: "png", scale: 1 });
  await fs.writeFile(
    path.join(renderDir, `slide-${String(index + 1).padStart(2, "0")}.png`),
    new Uint8Array(await png.arrayBuffer()),
  );
}
const montage = await deck.export({ format: "webp", montage: true });
await fs.writeFile(
  path.join(workspace, ".codex-ppt-build/bukal-pitch-montage-v4.webp"),
  new Uint8Array(await montage.arrayBuffer()),
);
console.log(`Rendered ${deck.slides.items.length} slides`);
