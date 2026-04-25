# Page snapshot

```yaml
- generic [ref=e5]:
  - generic [ref=e6]:
    - img [ref=e8]
    - heading "HLM CRM" [level=1] [ref=e12]
    - paragraph [ref=e13]: HLM Login
  - generic [ref=e14]:
    - alert [ref=e15]:
      - img [ref=e16]
      - text: Request failed (500)
    - generic [ref=e19]:
      - generic [ref=e20]:
        - generic [ref=e21]: Email
        - textbox "Email" [ref=e22]: admin@acme.com
      - generic [ref=e23]:
        - generic [ref=e24]: Mot de passe
        - generic [ref=e25]:
          - textbox "Mot de passe" [ref=e26]
          - button "Afficher le mot de passe" [ref=e27] [cursor=pointer]:
            - img [ref=e28]
      - button "Se connecter" [disabled] [ref=e31]
  - generic [ref=e32]:
    - generic [ref=e33]: Langue
    - generic [ref=e35]:
      - img [ref=e36]
      - button "FR" [ref=e39] [cursor=pointer]
      - button "EN" [ref=e40] [cursor=pointer]
      - button "عربي" [ref=e41] [cursor=pointer]
```