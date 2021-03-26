const displayStandardMliOptions = () => {
    if (Alteryx.Gui.Manager.getDataItem('IsTimeSeries').getValue()) {
        document.getElementById('datasetkey').style.display = 'none'
        document.getElementById('targetcolumn').style.display = 'none'
    } else {
        document.getElementById('datasetkey').style.display = 'initial'
        document.getElementById('targetcolumn').style.display = 'initial'
    }
}

const toggleOAuth = () => {
    if (Alteryx.Gui.Manager.getDataItem('UseOAuth').getValue()) {
        document.getElementById('usernameBox').style.display = 'none'
        document.getElementById('passwordBox').style.display = 'none'

        document.getElementById('endpointURL').style.display = 'initial'
        document.getElementById('introspectionURL').style.display = 'initial'
        document.getElementById('clientID').style.display = 'initial'
        document.getElementById('refreshToken').style.display = 'initial'
    } else {
        document.getElementById('usernameBox').style.display = 'initial'
        document.getElementById('passwordBox').style.display = 'initial'

        document.getElementById('endpointURL').style.display = 'none'
        document.getElementById('introspectionURL').style.display = 'none'
        document.getElementById('clientID').style.display = 'none'
        document.getElementById('refreshToken').style.display = 'none'
    }
}

Alteryx.Gui.BeforeLoad = function (manager, AlteryxDataItems, json) {
    displayStandardMliOptions()
    toggleOAuth()
}

Alteryx.Gui.AfterLoad = function (manager) {
    displayStandardMliOptions()
    toggleOAuth()
    Alteryx.Gui.Manager.getDataItem('IsTimeSeries').registerPropertyListener('value', displayStandardMliOptions)
    Alteryx.Gui.Manager.getDataItem('UseOAuth').registerPropertyListener('value', toggleOAuth)
}