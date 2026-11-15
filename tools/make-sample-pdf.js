// Generates a small, valid 3-page PDF for emulator smoke tests.
const fs = require("fs");

const pages = [
  {
    title: "Study Notes - Lecture 1",
    lines: [
      "Linear Algebra: Vector Spaces",
      "",
      "1. A vector space V over a field F is a set with addition",
      "   and scalar multiplication satisfying eight axioms.",
      "2. A basis is a linearly independent spanning set.",
      "3. Dimension is the number of vectors in any basis.",
      "",
      "Key exam point: every spanning set contains a basis.",
    ],
  },
  {
    title: "Study Notes - Lecture 2",
    lines: [
      "Linear Maps and Matrices",
      "",
      "A linear map T: V -> W satisfies T(u+v) = T(u) + T(v).",
      "The matrix of T depends on the chosen bases.",
      "Rank-nullity: dim V = rank T + nullity T.",
      "",
      "Practice: compute the kernel of a 3x3 matrix.",
    ],
  },
  {
    title: "Study Notes - Lecture 3",
    lines: [
      "Eigenvalues and Diagonalization",
      "",
      "lambda is an eigenvalue when det(A - lambda I) = 0.",
      "Eigenvectors of distinct eigenvalues are independent.",
      "A is diagonalizable iff it has a full eigenbasis.",
      "",
      "Next week: inner product spaces and Gram-Schmidt.",
    ],
  },
];

const escape = (s) => s.replace(/\\/g, "\\\\").replace(/\(/g, "\\(").replace(/\)/g, "\\)");

const objects = [];
objects[1] = "<< /Type /Catalog /Pages 2 0 R >>";
objects[2] = `<< /Type /Pages /Kids [4 0 R 6 0 R 8 0 R] /Count 3 >>`;
objects[3] = "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>";

pages.forEach((page, index) => {
  const pageObj = 4 + index * 2;
  const contentObj = pageObj + 1;
  objects[pageObj] =
    `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] ` +
    `/Resources << /Font << /F1 3 0 R >> >> /Contents ${contentObj} 0 R >>`;

  let stream = "BT\n/F1 26 Tf\n72 760 Td\n(" + escape(page.title) + ") Tj\nET\n";
  stream += "0.2 0.3 0.5 RG\n2 w\n72 742 m\n523 742 l\nS\n";
  let y = 706;
  for (const line of page.lines) {
    if (line.length > 0) {
      stream += `BT\n/F1 13 Tf\n72 ${y} Td\n(${escape(line)}) Tj\nET\n`;
    }
    y -= 26;
  }
  objects[contentObj] =
    `<< /Length ${Buffer.byteLength(stream, "latin1")} >>\nstream\n${stream}endstream`;
});

let pdf = "%PDF-1.4\n%\xE2\xE3\xCF\xD3\n";
const offsets = [];
for (let i = 1; i < objects.length; i++) {
  offsets[i] = Buffer.byteLength(pdf, "latin1");
  pdf += `${i} 0 obj\n${objects[i]}\nendobj\n`;
}
const xrefOffset = Buffer.byteLength(pdf, "latin1");
pdf += `xref\n0 ${objects.length}\n0000000000 65535 f \n`;
for (let i = 1; i < objects.length; i++) {
  pdf += String(offsets[i]).padStart(10, "0") + " 00000 n \n";
}
pdf += `trailer\n<< /Size ${objects.length} /Root 1 0 R >>\nstartxref\n${xrefOffset}\n%%EOF\n`;

fs.writeFileSync(process.argv[2], Buffer.from(pdf, "latin1"));
console.log("wrote", process.argv[2], Buffer.byteLength(pdf, "latin1"), "bytes");
