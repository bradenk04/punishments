# Contributing
Ensure your code is concise and readable. Make your changes small and PR/Merge often to keep codebase up to date. You are responsible for every line of code that you write and push.

## Process
1. Open an issue using one of the premade issue formats (Excluding trivial fixes and doc changes).
2. Wait for a maintainer to approve the approach you suggest before you begin working on anything non-trivial
3. Make your branch from main (NOT another branch unless absolutely necessary) with this naming scheme:
  - `feature/<slug>`
  - `fix/<slug>`
  - `docs/<slug>`
  - `chore/<slug>`
  - `hotfix/<slug>`
4. Open a PR that links the issue (`Closes #N`) and fills out every section of the PR template. Ensure PR & issue text is human written exclusively.
5. Address review feedback. PRs are squash-merged exclusively into `main`.

Slugs are all lowercase and hyphen-seperated

## Pull Requests
- One major change per PR.
- Keep PRs under about 400 lines changed. If you changed more it should be multiple PRs (or PR stack).
- Do not reformat or reorder code you are not changing
- Described what you verified and how. Untested changes will not be merged or reviewed.

## Code Standards
- Minimal code. Delete before adding.
- No comments unless they're absolutely needed to understand what is writing. Code should be self documenting
- No speculative abstractions or unrequested config.
- No new dependencies without explicit approval.
- Immutable by default. No null for absence.
- Never swallow exceptions.
- No I/O on the main thread.

## AI-Assisted Contributions
AI tools are fine, unreviewed AI output is absolutely not.
- Read and understand every line the AI wrote before committing anything.
- Disclose AI assistance in your PR.
- Be ready to explain any change without any tools, if you cannot then your PR will be closed.
- Do not let agents commit, push, or open PRs on your behalf. Ensure you do it yourself and don't use AI generated content for PRs.

## Review
Maintainers may ask why any one line exists and what its purpose is. A change without a clear answer will not be merged. PRs that ignore the template, exceed 400 lines, or show no signs of a human being in the loop and reviewing code will be closed with little to no feedback.

## Conduct
Be respectful and direct. Disagree about code, not people.
