// @vitest-environment jsdom

import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { RouterProvider, createMemoryRouter, useLocation } from "react-router-dom";

import { Sidebar } from "@/components/sidebar";

function SidebarTestShell() {
  const location = useLocation();

  return (
    <>
      <Sidebar
        lists={[]}
        isLoading={false}
        currentSpace={location.pathname === "/calendar" ? "calendar" : "tasks"}
        taskSpacePath="/today"
        onCreateList={() => undefined}
        onEditList={() => undefined}
        onReorderLists={async () => undefined}
        onHide={() => undefined}
        onResizeStart={() => undefined}
      />
      <div data-testid="pathname">{location.pathname}</div>
    </>
  );
}

describe("Sidebar task and calendar switch", () => {
  it("navigates between calendar and the last task route", async () => {
    const router = createMemoryRouter(
      [
        { path: "/calendar", element: <SidebarTestShell /> },
        { path: "/today", element: <SidebarTestShell /> },
      ],
      {
        initialEntries: ["/calendar"],
      },
    );

    render(<RouterProvider router={router} />);

    expect(screen.getByTestId("pathname").textContent).toBe("/calendar");
    expect(screen.getByRole("link", { name: "Task" }).getAttribute("href")).toBe("/today");

    fireEvent.click(screen.getByRole("link", { name: "Task" }));

    await waitFor(() => {
      expect(screen.getByTestId("pathname").textContent).toBe("/today");
    });

    fireEvent.click(screen.getByRole("link", { name: "Calendar" }));

    await waitFor(() => {
      expect(screen.getByTestId("pathname").textContent).toBe("/calendar");
    });
  });
});
