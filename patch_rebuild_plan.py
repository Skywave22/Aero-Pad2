with open('REBUILD_PLAN.md', 'r') as f:
    text = f.read()

text = text.replace('- [ ] **Phase 2** — Global input safety / stuck input fixes', '- [x] **Phase 2** — Global input safety / stuck input fixes')

with open('REBUILD_PLAN.md', 'w') as f:
    f.write(text)
