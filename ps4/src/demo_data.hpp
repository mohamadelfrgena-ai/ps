#pragma once

#include "addonstation_api.hpp"

namespace ps4app {

// Local, fictional catalog shown when Supabase is not configured or cannot be
// reached. This data is never uploaded to the database.
addonstation::CatalogSnapshot makeDemoCatalog();

} // namespace ps4app
