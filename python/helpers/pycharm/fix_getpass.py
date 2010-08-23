# on win32, redefines getpass from too clandestine to more explicit
import sys
def fixGetpass():
    if sys.platform == 'win32':
        import getpass
        import warnings
        fallback = getattr(getpass, 'fallback_getpass', None) # >= 2.6
        if not fallback:
            fallback = getpass.default_getpass # <= 2.5
        getpass.getpass = getpass.fallback_getpass
        warnings.simplefilter("ignore", category=getpass.GetPassWarning)

