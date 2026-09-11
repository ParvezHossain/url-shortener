import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, test, vi } from "vitest";
import { CreateUrlForm } from "./CreateUrlForm";

const create = vi.fn();
beforeEach(() => {
  create.mockReset();
  vi.stubGlobal(
    "fetch",
    vi.fn((url, options) =>
      url === "/ui/config"
        ? Promise.resolve(
            new Response(
              JSON.stringify({ publicBaseUrl: "https://links.example.test" }),
            ),
          )
        : create(url, options),
    ),
  );
});
function fill(name: string, value: string) {
  fireEvent.change(screen.getByLabelText(name), { target: { value } });
}
function submit() {
  fireEvent.submit(screen.getByRole("form"));
}
function success() {
  return new Response(
    JSON.stringify({ shortUrl: "https://links.example.test/abc" }),
    { status: 201 },
  );
}

test("CreateUrlForm_validMinimumInput_submitsExpectedPayload", async () => {
  create.mockResolvedValue(success());
  render(<CreateUrlForm />);
  fill("Destination URL", "https://example.com/path");
  submit();
  await screen.findByText(/Your short link is ready/);
  expect(create).toHaveBeenCalledWith(
    "/api/v1/urls",
    expect.objectContaining({
      method: "POST",
      body: JSON.stringify({ originalUrl: "https://example.com/path" }),
    }),
  );
  expect(
    screen.getByRole("link", { name: "https://links.example.test/abc" }),
  ).toHaveAttribute("href", "https://links.example.test/abc");
});

test("CreateUrlForm_optionalAliasAndExpiry_submitsExpectedPayload", async () => {
  create.mockResolvedValue(success());
  render(<CreateUrlForm />);
  await userEvent.setup().click(screen.getByText("Customize your link"));
  expect(await screen.findByText("https://links.example.test/")).toBeVisible();
  fill("Destination URL", "https://example.com");
  fill("Custom alias (optional)", "Launch_26");
  fill("Expiry (optional)", "2099-10-20T14:30");
  submit();
  await screen.findByText(/Your short link is ready/);
  expect(JSON.parse(create.mock.calls[0][1].body)).toEqual({
    originalUrl: "https://example.com",
    customAlias: "Launch_26",
    expiresAt: new Date("2099-10-20T14:30").toISOString(),
  });
});

test.each([
  "",
  "example.com",
  "javascript:alert(1)",
  "https://",
  "https://example.com/a b",
])(
  "CreateUrlForm_invalidInput_blocksSubmissionAndShowsMessage (%s)",
  async (value) => {
    render(<CreateUrlForm />);
    fill("Destination URL", value);
    submit();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Check the highlighted fields",
    );
    expect(screen.getByLabelText("Destination URL")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    expect(screen.getByLabelText("Destination URL")).toHaveFocus();
    expect(create).not.toHaveBeenCalled();
  },
);

test.each(["ab", "a".repeat(17), "bad alias", "bad!"])(
  "CreateUrlForm_invalidAlias_blocksRequest (%s)",
  async (alias) => {
    render(<CreateUrlForm />);
    fill("Destination URL", "https://example.com");
    fill("Custom alias (optional)", alias);
    submit();
    expect(
      await screen.findByText(
        "Use 3–16 letters, numbers, underscores, or hyphens.",
      ),
    ).toBeVisible();
    expect(create).not.toHaveBeenCalled();
  },
);

test("CreateUrlForm_pastExpiry_blocksRequest", async () => {
  render(<CreateUrlForm />);
  fill("Destination URL", "https://example.com");
  fill("Expiry (optional)", "2000-01-01T12:00");
  submit();
  expect(
    await screen.findByText("Choose a future date and time."),
  ).toBeVisible();
  expect(create).not.toHaveBeenCalled();
});

test("CreateUrlForm_duplicateAlias_showsAliasError", async () => {
  create.mockResolvedValue(
    new Response(JSON.stringify({ detail: "Alias already exists" }), {
      status: 409,
    }),
  );
  render(<CreateUrlForm />);
  fill("Destination URL", "https://example.com");
  fill("Custom alias (optional)", "taken");
  submit();
  expect(
    await screen.findByText("This alias is already taken. Choose another one."),
  ).toBeVisible();
  expect(screen.getByLabelText("Custom alias (optional)")).toHaveValue("taken");
  expect(screen.getByLabelText("Custom alias (optional)")).toHaveFocus();
});

test.each(["network", "server"])(
  "CreateUrlForm_serverUnavailable_preservesInputAndShowsRetryMessage (%s)",
  async (kind) => {
    if (kind === "network")
      create.mockRejectedValue(new TypeError("Network error"));
    else create.mockResolvedValue(new Response("Unavailable", { status: 503 }));
    render(<CreateUrlForm />);
    fill("Destination URL", "https://example.com");
    fill("Custom alias (optional)", "my-link");
    fill("Expiry (optional)", "2099-01-01T12:00");
    submit();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Please try again",
    );
    expect(screen.getByLabelText("Destination URL")).toHaveValue(
      "https://example.com",
    );
    expect(screen.getByLabelText("Custom alias (optional)")).toHaveValue(
      "my-link",
    );
    expect(screen.getByLabelText("Expiry (optional)")).toHaveValue(
      "2099-01-01T12:00",
    );
    create.mockResolvedValue(success());
    submit();
    await screen.findByText(/Your short link is ready/);
  },
);

test("CreateUrlForm_validationProblem_mapsFieldMessages", async () => {
  create.mockResolvedValue(
    new Response(
      JSON.stringify({
        detail: "Request validation failed",
        errors: [
          "expiresAt: must be in the future",
          "customAlias: invalid alias",
        ],
      }),
      { status: 400 },
    ),
  );
  render(<CreateUrlForm />);
  fill("Destination URL", "https://example.com");
  submit();
  expect(await screen.findByText("must be in the future")).toBeVisible();
  expect(
    screen.getByLabelText("Custom alias (optional)"),
  ).toHaveAccessibleDescription(expect.stringContaining("invalid alias"));
});

test("CreateUrlForm_invalidUrlProblem_mapsDestinationError", async () => {
  create.mockResolvedValue(
    new Response(
      JSON.stringify({ detail: "Original URL exceeds maximum length" }),
      { status: 400 },
    ),
  );
  render(<CreateUrlForm />);
  fill("Destination URL", "https://example.com");
  submit();
  await waitFor(() =>
    expect(screen.getByLabelText("Destination URL")).toHaveAttribute(
      "aria-invalid",
      "true",
    ),
  );
});

test("CreateUrlForm_pending_preventsRepeatedSubmission", async () => {
  let resolve!: (value: Response) => void;
  create.mockReturnValue(
    new Promise<Response>((done) => {
      resolve = done;
    }),
  );
  render(<CreateUrlForm />);
  fill("Destination URL", "https://example.com");
  submit();
  submit();
  expect(
    screen.getByRole("button", { name: /Creating your link/ }),
  ).toBeDisabled();
  expect(screen.getByRole("status")).toHaveTextContent("Working");
  expect(create).toHaveBeenCalledOnce();
  await act(async () => resolve(success()));
  expect(screen.getByRole("button", { name: /Shorten link/ })).toBeEnabled();
});

test("CreateUrlForm_configUnavailable_allowsRetry", async () => {
  vi.mocked(fetch).mockRejectedValueOnce(new Error("Offline"));
  render(<CreateUrlForm />);
  await userEvent.setup().click(screen.getByText("Customize your link"));
  await userEvent
    .setup()
    .click(await screen.findByRole("button", { name: "Retry loading prefix" }));
  expect(await screen.findByText("https://links.example.test/")).toBeVisible();
});
