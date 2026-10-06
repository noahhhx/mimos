# Use Mimos from an AI agent

You can connect an AI agent, such as Claude Code or Claude on claude.ai, to
your Mimos and ask it for things in plain words. The agent signs in as you
and works with your recipes, plans, shopping lists, and food log, the same
way you do in the app.

Mimos speaks the Model Context Protocol (MCP), which is how these agents
connect to outside services. You need one address: your Mimos API's
address with `/mcp` on the end. On the local development stack that is
`http://localhost:8080/mcp`. On a self-hosted Mimos it is your API URL,
for example `https://api.mimos.example.home/mcp`. Ask whoever runs your
Mimos if you're not sure.

## Connect Claude Code

1. Add Mimos as an MCP server. Replace the address with yours:

    ```bash
    claude mcp add --transport http --client-id mimos-agent --callback-port 8976 mimos https://api.mimos.example.home/mcp
    ```

    Any free port works for `--callback-port`.

2. In Claude Code, run `/mcp`, choose **mimos**, and sign in.

Your browser opens the Mimos sign-in page. After you sign in, Claude Code
lists the Mimos tools and you can start asking.

## Connect Claude on claude.ai

1. In claude.ai, open **Settings**, then **Connectors**, and add a custom
   connector.
2. Enter your Mimos address ending in `/mcp`.
3. Under the advanced settings, set the OAuth client ID to `mimos-agent`.
   Leave the client secret empty.
4. Choose **Connect** and sign in to Mimos.

claude.ai reaches your Mimos from the internet, so both the API and the
sign-in page must be on public HTTPS addresses. A Mimos that only your home
network can reach works with Claude Code, not with claude.ai.

## Ask for things

Some prompts to start with:

- "Create my meal plan for next week: dinners only, mostly from the
  library."
- "What recipes including beef do I have?"
- "What is still left on my shopping list?"
- "Create a new recipe for me: my grandmother's lentil soup, four
  servings." The agent asks for anything it needs, or you can paste the
  recipe.
- "Log yesterday's lunch: the chili from the library, two servings."
- "Turn on Country of the Week and plan Thursday from its suggestions."

Search covers titles, descriptions, tags, and ingredient names, in your
recipes and in the library.

## What an agent can and cannot do

An agent signed in to your Mimos can do what you can do in the app:

- Read, search, write, change, and delete your household's recipes.
- Read and search the library.
- Plan meals, change servings and who eats them, and remove planned meals.
- Generate your shopping list and tick items off.
- Log meals, including your share of a meal you shared, and read your daily totals.
- Turn plugins on or off and read their suggestions.
- See who is in your household and create an invite link.

It can't:

- See or change another household's recipes, plans, or shopping lists, or
  anyone else's log.
- Join or leave a household for you. You do that yourself in the app.
- Change library recipes.
- Export or import your whole account. Use [Your data](your-data.md) for
  that.

The agent acts as you. Anything it changes is changed in your account,
exactly as if you had done it in the app. Read what it plans to do before
you let it delete things.

## Stop an agent

Remove the Mimos server from the agent: `claude mcp remove mimos` in Claude
Code, or remove the connector in claude.ai's settings.

Agents usually ask to stay signed in after you close the browser, so
signing out of Mimos does not end their access. To end it, open your
account page (your sign-in address followed by `/realms/mimos/account`),
go to **Applications**, and remove access for **Mimos Agent**.
