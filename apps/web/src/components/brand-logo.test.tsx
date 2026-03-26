import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";

import { BrandLogo } from "@/components/brand-logo";

describe("BrandLogo", () => {
  it("renders the provided logo asset as the app brand source", () => {
    const html = renderToStaticMarkup(createElement(BrandLogo, { className: "brand" }));

    expect(html).toContain("/task-manager-logo.svg");
    expect(html).toContain("Task Manager logo");
  });
});
