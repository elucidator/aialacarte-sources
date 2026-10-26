# Exercise 4 – Code review

Review the change described in `review/CHANGE.md`. The diff is in `review/candidate.diff`,
the previous version of the changed file is in `review/base/`, and the workspace already
contains the candidate version so you can build and read it in context.

Read `review/GATEWAY-CONTRACT.md` before you start: it is the contract of the system the
changed code talks to.

## Task

Write a review as you would for a pull request:

- Would you approve, request changes, or reject? Say why.
- List concrete findings, ordered by severity. For each finding state the scenario in which it
  matters and what you would change.
- If you request changes, describe the corrected behaviour precisely enough that the author
  could implement it without further questions.

This exercise is review only. Do not modify the code in this workspace.
