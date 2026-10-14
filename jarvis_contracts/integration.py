class IncompatibleProvider(ValueError):
    pass

class MissingCapability(ValueError):
    pass

class CapabilityRegistry:
    def __init__(self):
        self.providers = {}

    def register(self, name, version, capabilities):
        self._version(version)
        self.providers[name] = (version, frozenset(capabilities))

    @staticmethod
    def _version(value):
        parts = value.split('.')
        if len(parts) != 2 or not all(p.isascii() and p.isdigit() for p in parts):
            raise ValueError('invalid version')
        return tuple(map(int, parts))

    def require(self, name, version, capability):
        requested = self._version(version)
        provider = self.providers.get(name)
        if provider is None:
            raise IncompatibleProvider('missing provider')
        offered = self._version(provider[0])
        if offered[0] != requested[0] or offered[1] < requested[1]:
            raise IncompatibleProvider('incompatible version')
        if capability not in provider[1]:
            raise MissingCapability('missing capability')

    def supports(self, name, version, capability):
        try:
            self.require(name, version, capability)
            return True
        except (IncompatibleProvider, MissingCapability):
            return False
