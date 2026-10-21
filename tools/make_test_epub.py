#!/usr/bin/env python3
"""
Generate a minimal, valid EPUB 3 without a cover image.
Used for testing the library screen's no-cover fallback placeholder.
"""

import argparse
import io
import os
import zipfile

def create_minimal_epub(output_path: str, title: str = "Test Book Without Cover", author: str = "Test Author"):
    buffer = io.BytesIO()

    with zipfile.ZipFile(buffer, "w") as zf:
        # 1. 'mimetype' MUST be uncompressed (STORED) and the first entry in the archive.
        zf.writestr("mimetype", "application/epub+zip", compress_type=zipfile.ZIP_STORED)

        # 2. META-INF/container.xml
        container_xml = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""
        zf.writestr("META-INF/container.xml", container_xml, compress_type=zipfile.ZIP_DEFLATED)

        # 3. OEBPS/content.opf (Package metadata, manifest, and spine without any cover item)
        content_opf = f"""<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="pub-id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="pub-id">urn:uuid:9a7f34c2-0000-4000-8000-000000000001</dc:identifier>
    <dc:title>{title}</dc:title>
    <dc:creator>{author}</dc:creator>
    <dc:language>en</dc:language>
    <meta property="dcterms:modified">2026-10-21T00:00:00Z</meta>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="chapter1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine>
    <itemref idref="chapter1"/>
  </spine>
</package>"""
        zf.writestr("OEBPS/content.opf", content_opf, compress_type=zipfile.ZIP_DEFLATED)

        # 4. OEBPS/nav.xhtml (EPUB 3 Table of Contents)
        nav_xhtml = f"""<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head>
  <title>{title}</title>
</head>
<body>
  <nav epub:type="toc" id="toc">
    <h1>Table of Contents</h1>
    <ol>
      <li><a href="chapter1.xhtml">Chapter 1</a></li>
    </ol>
  </nav>
</body>
</html>"""
        zf.writestr("OEBPS/nav.xhtml", nav_xhtml, compress_type=zipfile.ZIP_DEFLATED)

        # 5. OEBPS/chapter1.xhtml
        chapter1_xhtml = f"""<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title>Chapter 1</title>
</head>
<body>
  <h1>Chapter 1</h1>
  <p>This is a test book generated without a cover image to verify the fallback placeholder card.</p>
</body>
</html>"""
        zf.writestr("OEBPS/chapter1.xhtml", chapter1_xhtml, compress_type=zipfile.ZIP_DEFLATED)

    os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)
    with open(output_path, "wb") as f:
        f.write(buffer.getvalue())

    print(f"Generated minimal valid EPUB: {output_path} ({len(buffer.getvalue())} bytes)")

def main():
    parser = argparse.ArgumentParser(description="Generate minimal valid EPUB without cover image.")
    parser.add_argument("-o", "--output", default="tools/no_cover_test.epub", help="Output .epub path")
    parser.add_argument("-t", "--title", default="Test Book Without Cover", help="Book title")
    parser.add_argument("-a", "--author", default="Test Author", help="Book author")
    args = parser.parse_args()

    create_minimal_epub(args.output, args.title, args.author)

if __name__ == "__main__":
    main()
