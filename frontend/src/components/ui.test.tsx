import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test, vi } from "vitest";
import {
  Alert,
  Button,
  Card,
  Input,
  Modal,
  Skeleton,
  Spinner,
  Toast,
} from "./ui";

test("Button_keyboardActivation_invokesAction", async () => {
  const action = vi.fn();
  const user = userEvent.setup();
  render(<Button onClick={action}>Continue</Button>);
  await user.tab();
  await user.keyboard("{Enter}");
  await user.keyboard(" ");
  expect(action).toHaveBeenCalledTimes(2);
});

test("Button_loading_preventsActivationAndAnnouncesWork", async () => {
  const action = vi.fn();
  render(
    <Button loading onClick={action}>
      Save
    </Button>,
  );
  await userEvent.setup().click(screen.getByRole("button"));
  expect(action).not.toHaveBeenCalled();
  expect(screen.getByRole("button")).toBeDisabled();
  expect(screen.getByRole("status")).toHaveTextContent("Working");
});

test("Input_invalidValue_associatesLabelHintAndError", () => {
  render(<Input label="Destination" hint="Use HTTPS" error="Enter a URL" />);
  const input = screen.getByRole("textbox", { name: "Destination" });
  expect(input).toHaveAccessibleDescription("Use HTTPS Enter a URL");
  expect(input).toHaveAttribute("aria-invalid", "true");
});

test("Card_content_rendersChildren", () => {
  render(
    <Card>
      <h2>Link details</h2>
    </Card>,
  );
  expect(screen.getByRole("heading", { name: "Link details" })).toBeVisible();
});

test.each(["info", "success", "error"] as const)(
  "Alert_%s_announcesFeedback",
  (tone) => {
    render(<Alert tone={tone}>Feedback</Alert>);
    expect(
      screen.getByRole(tone === "error" ? "alert" : "status"),
    ).toHaveTextContent("Feedback");
  },
);

test("Spinner_loading_exposesAccessibleStatus", () => {
  render(<Spinner label="Loading links" />);
  expect(screen.getByRole("status")).toHaveTextContent("Loading links");
});

test("Skeleton_loading_hidesDecorativePlaceholder", () => {
  const { container } = render(<Skeleton />);
  expect(container.firstChild).toHaveAttribute("aria-hidden", "true");
});

test("Modal_openAndClose_usesNativeDialogAndNotifiesOwner", async () => {
  vi.spyOn(HTMLDialogElement.prototype, "showModal").mockImplementation(
    function (this: HTMLDialogElement) {
      this.open = true;
    },
  );
  vi.spyOn(HTMLDialogElement.prototype, "close").mockImplementation(function (
    this: HTMLDialogElement,
  ) {
    this.open = false;
  });
  const close = vi.fn();
  const view = render(
    <Modal open title="Details" onClose={close}>
      Link content
    </Modal>,
  );
  const dialog = screen.getByRole("dialog", { name: "Details" });
  expect(dialog).toHaveAttribute("open");
  await userEvent
    .setup()
    .click(screen.getByRole("button", { name: "Close dialog" }));
  expect(close).toHaveBeenCalledOnce();
  dialog.dispatchEvent(new Event("cancel", { cancelable: true }));
  expect(close).toHaveBeenCalledTimes(2);
  view.rerender(
    <Modal open={false} title="Details" onClose={close}>
      Link content
    </Modal>,
  );
  expect(dialog).not.toHaveAttribute("open");
});

test("Toast_dismiss_invokesCallback", async () => {
  const dismiss = vi.fn();
  render(<Toast message="Link copied" onDismiss={dismiss} />);
  expect(screen.getByRole("status")).toHaveTextContent("Link copied");
  await userEvent
    .setup()
    .click(screen.getByRole("button", { name: "Dismiss notification" }));
  expect(dismiss).toHaveBeenCalledOnce();
});
